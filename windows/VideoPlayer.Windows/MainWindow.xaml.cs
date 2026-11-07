using System.ComponentModel;
using System.IO;
using System.Reflection;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using Microsoft.Web.WebView2.Core;
using Microsoft.Win32;

namespace VideoPlayer.Windows;
public partial class MainWindow : Window
{
    private enum Mode { Results, Search, Movie }
    private Mode mode;
    private readonly Presentation presentation = new();
    private readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(1) };
    private readonly Queue<string> log = new();
    private bool ready, busy, closed, fullscreen, routed, retryUsed, initializing;
    private bool searching, returnPending;
    private bool googleSearch, searchDocumentReady;
    private string activeQuery="";
    private DateTime homeReady, searchDeadline;
    private int epoch, searchTicks;
    private Source source;
    private MovieCollections? collections;
    private string collection="results";
    private List<MovieCard> searchCards=[];
    private MovieCard? currentMovie;
    private string? searchTarget, movieTarget;
    private WindowState savedState;
    private WindowStyle savedStyle;
    private ResizeMode savedResize;
    private readonly bool smoke = Environment.GetCommandLineArgs().Contains("--smoke-test");
    private readonly string profile = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "VideoPlayer", "WebView2");
    public MainWindow()
    {
        InitializeComponent();
        timer.Tick += async (_, _) => await Tick();
        SearchButton.IsEnabled = false;
    }
    private static string Script(string name)
    {
        var assembly = Assembly.GetExecutingAssembly();
        var resource = assembly.GetManifestResourceNames().Single(n => n.EndsWith("." + name, StringComparison.Ordinal));
        using var stream = assembly.GetManifestResourceStream(resource)!;
        using var reader = new StreamReader(stream);
        return reader.ReadToEnd();
    }
    private void Log(string text, string? url = null)
    {
        log.Enqueue($"{DateTimeOffset.Now:O} {text} {(url == null ? "" : Policy.Redact(url))}");
        while (log.Count > 150) log.Dequeue();
    }
    private async Task<string?> Eval(string script)
    {
        var raw = await Browser.ExecuteScriptAsync(script).WaitAsync(TimeSpan.FromSeconds(8));
        return raw is "null" or "undefined" ? null : JsonSerializer.Deserialize<string>(raw);
    }
    private async void WindowLoaded(object sender, RoutedEventArgs e)
    {
        if(initializing)return;initializing=true;
        try
        {
            var path = smoke ? Path.Combine(Path.GetTempPath(), "VideoPlayer-smoke-" + Guid.NewGuid().ToString("N")) : profile;
            try { collections=new MovieCollections(Path.Combine(smoke?path:Path.GetDirectoryName(path)!,"collections.json")); }
            catch (Exception error) when(error is IOException or UnauthorizedAccessException or JsonException)
            { Log("collections.load."+error.GetType().Name); }
            var environment = await CoreWebView2Environment.CreateAsync(null, path, new CoreWebView2EnvironmentOptions { AdditionalBrowserArguments="--autoplay-policy=no-user-gesture-required" });
            await Browser.EnsureCoreWebView2Async(environment);
            if (closed) return;
            var core = Browser.CoreWebView2;
            core.Settings.AreDevToolsEnabled = false;
            core.Settings.IsStatusBarEnabled = false;
            core.Settings.AreHostObjectsAllowed = false;
            core.Settings.IsWebMessageEnabled = true;
            await ConfigurePlayback(core);
            core.NavigationStarting += Navigating;
            core.NavigationCompleted += (_, args) =>
            {
                if(mode==Mode.Search)searchDocumentReady=args.IsSuccess;
                if (!args.IsSuccess) { Status.Text = "Nie udało się wczytać strony. Spróbuj ponownie lub wybierz Pokaż stronę."; Log("navigation.failed." + args.WebErrorStatus); }
                else { Log("navigation.complete", core.Source); _=UpdateMetadata(core.Source,epoch); }
            };
            core.NewWindowRequested += (_, args) =>
            {
                args.Handled = true;
                if (args.IsUserInitiated && Policy.Allowed(args.Uri)) core.Navigate(args.Uri);
                else Log("popup.blocked");
            };
            core.DownloadStarting += (_, args) => { args.Cancel = true; Status.Text = "Ta wersja aplikacji służy do odtwarzania filmów."; };
            core.PermissionRequested += (_, args) => args.State = CoreWebView2PermissionState.Deny;
            core.ProcessFailed += (_, args) => { ExitFullscreen(); ShowBrowser(); Status.Text = "Proces przeglądarki został przerwany. Zamknij i uruchom aplikację ponownie."; Log("browser.process_failed." + args.ProcessFailedKind); };
            core.ContainsFullScreenElementChanged += (_, _) =>
            {
                if (core.ContainsFullScreenElement) { presentation.User(); EnterFullscreen(); }
                else ExitFullscreen();
            };
            core.ContextMenuRequested += (_, args) =>
            {
                var command = environment.CreateContextMenuItem("Pokaż przyciski / wyjdź z pełnego ekranu", null, CoreWebView2ContextMenuItemKind.Command);
                command.CustomItemSelected += (_, _) => Dispatcher.InvokeAsync(() => LeaveFullscreen());
                args.MenuItems.Insert(0, command);
            };
            PreviewKeyDown += (_, args) =>
            {
                if (args.Key == Key.F11) { ToggleFullscreen(); args.Handled = true; }
                else if (args.Key == Key.Escape && fullscreen) { LeaveFullscreen(); args.Handled = true; }
            };
            ready = true; SearchButton.IsEnabled = true;
            Status.Text = collections==null?"Nie można odczytać lokalnych kolekcji. Historia i ulubione są niedostępne; plik pozostaje zachowany.":"Wpisz tytuł filmu lub wklej link Vider / Freedisc.";
            Query.Focus(); timer.Start();
            if (smoke) await SmokeTest();
        }
        catch (Exception error)
        {
            Log("startup." + error.GetType().Name);
            Status.Text = "Nie udało się uruchomić przeglądarki.";
            if (smoke) { WriteSmoke("FAIL: " + error); Application.Current.Shutdown(1); return; }
            MessageBox.Show(this, "Do działania potrzebny jest Microsoft Edge WebView2 Runtime.\nZainstaluj go z: https://developer.microsoft.com/microsoft-edge/webview2/\n\nSzczegóły: " + error.Message, "Uruchamianie VideoPlayer", MessageBoxButton.OK, MessageBoxImage.Information);
        }
    }
    private void Navigating(object? sender, CoreWebView2NavigationStartingEventArgs args)
    {
        if ((smoke || mode == Mode.Results) && args.Uri == "about:blank") return;
        if (!(mode==Mode.Search && googleSearch ? Policy.Google(args.Uri) : Policy.Allowed(args.Uri)) || mode == Mode.Search && !googleSearch && Policy.Site(args.Uri) != source)
        {
            args.Cancel = true; Log("navigation.blocked", args.Uri);
            Status.Text = "Link prowadzi poza obsługiwany serwis. Pozostajemy w aplikacji."; return;
        }
        ++epoch;searchDocumentReady=false;
        if (mode == Mode.Movie)
        {
            ExitFullscreen(); presentation.Navigation();
            if(Policy.Movie(args.Uri))
            {
                var known=currentMovie!=null && MovieCollections.Key(currentMovie.url)==MovieCollections.Key(args.Uri)?currentMovie:collections?.Find(args.Uri)?.Card ?? new MovieCard("",args.Uri,"");
                currentMovie=known with { url=args.Uri }; CollectionAction(()=>collections?.Opened(currentMovie));
            }
            // Restore the selected movie once if the service redirects through its homepage.
            if (Policy.HomePage(args.Uri) && movieTarget != null && !retryUsed)
            {
                retryUsed = true;
                returnPending = true; homeReady = DateTime.UtcNow.AddSeconds(3);
            }
        }
        Log("navigation.start", args.Uri);
    }
    private async Task<string> ReadState()
    {
        var json = await Eval(Script("search-state.js"));
        using var doc = JsonDocument.Parse(json ?? "{}");
        return doc.RootElement.TryGetProperty("stage", out var value) ? value.GetString() ?? "results" : "results";
    }
    private async Task Tick()
    {
        if (!ready || busy || closed || mode == Mode.Results) return;
        if (mode == Mode.Search && searching && DateTime.UtcNow > searchDeadline)
        {
            if(!googleSearch){StartGoogle();return;}
            searching = false; ShowBrowser();
            Status.Text = "Upłynął czas wyszukiwania. Sprawdź stronę i wybierz Sprawdź wyniki lub ponów wyszukiwanie."; return;
        }
        busy = true; var current = epoch;
        try
        {
            var url = Browser.CoreWebView2.Source;
            if(mode==Mode.Search&&!searchDocumentReady)return;
            if (mode==Mode.Search && googleSearch) { await ReadGoogle(current); return; }
            if (!Policy.Allowed(url)) return;
            var stage = await ReadState();
            if (closed || current != epoch) return;
            verificationActive=stage!="results";
            if (stage != "results")
            {
                if (mode == Mode.Movie)
                {
                    presentation.Blocked(); ExitFullscreen();
                    await SetFilmLayout(false);
                    await Eval("(function(){if(!document.getElementById('vp-focus-style'))return 'page';return " + Script("focus-player.js") + "}())");
                }
                await Browser.ExecuteScriptAsync(Script("verification-layout.js"));
                ShowBrowser();
                Status.Text = "Serwis wymaga kodu, zgody lub logowania. Dokończ ten krok na stronie; aplikacja sprawdzi ją ponownie.";
                searchTicks = 0; searchDeadline = DateTime.UtcNow.AddSeconds(40); return;
            }
            if (mode == Mode.Search && searching)
            {
                if (source == Source.Freedisc && !routed && searchTarget != null)
                {
                    var result = await Eval(Script("freedisc-search.js").Replace("__VP_SEARCH_URL__", JsonSerializer.Serialize(searchTarget)));
                    if (closed || current != epoch) return;
                    routed = result == "routed";
                }
                if(!SearchDocumentMatches(url,source,activeQuery))return;
                var json = await Eval(Script("search-results.js"));
                if (closed || current != epoch) return;
                var found = JsonSerializer.Deserialize<List<MovieCard>>(json ?? "[]") ?? [];
                var movies = found.Where(m => Policy.Movie(m.url) && Policy.Site(m.url) == source).Take(20).ToList();
                if (movies.Count > 0) { searchCards=movies;collection="results";Render(movies); searching = false; Status.Text = $"Znaleziono: {movies.Count}. Kliknij miniaturkę, aby otworzyć film."; }
                else if (await SearchEmpty(current)) { searching=false;searchCards=[];Render([]);Status.Text="Brak wyników dla podanego tytułu."; }
                else if (++searchTicks >= 30)
                {
                    StartGoogle(); return;
                }
            }
            else if (mode == Mode.Movie)
            {
                if (returnPending && Policy.HomePage(url) && DateTime.UtcNow >= homeReady && movieTarget != null)
                { returnPending = false; Browser.CoreWebView2.Navigate(movieTarget); }
                else if (presentation.Pending && Policy.Movie(url)) await FocusMovie(true, current);
            }
        }
        catch (Exception error)
        {
            Log("inspect." + error.GetType().Name);
            if (++searchTicks >= 30 && mode == Mode.Search) { searching = false; ShowBrowser(); Status.Text = "Nie udało się odczytać strony. Wybierz Sprawdź wyniki lub ponów wyszukiwanie."; }
        }
        finally { busy = false; }
    }
    private async void Search()
    {
        if (!ready) return;
        var text = Query.Text.Trim();
        if (Policy.Movie(text)) { OpenMovie(text); return; }
        if (text.Length is < 1 or > 120) { Status.Text = "Wpisz tytuł (do 120 znaków) albo link do filmu."; return; }
        activeQuery=text; googleSearch=false;
        await FlushPlayback();
        Browser.CoreWebView2.Stop();
        LeaveFullscreen(); ++epoch; mode = Mode.Search; searching = true; routed = false; searchTicks = 0; searchDeadline = DateTime.UtcNow.AddSeconds(40); movieTarget = null; returnPending = false;
        collection="results";ClearCollection.Visibility=Visibility.Collapsed;searchCards=[];
        Cards.Children.Clear(); Welcome.Visibility = Visibility.Collapsed;
        PlayerTools.Visibility = Visibility.Collapsed; Browser.Visibility = Visibility.Hidden; Results.Visibility = Visibility.Visible;
        searchTarget = Policy.Search(source, text);
        Status.Text = "Wyszukiwanie · " + (source == Source.Vider ? "Vider" : "Freedisc") + "…";
        Browser.CoreWebView2.Navigate(source == Source.Freedisc ? Policy.Home(source) : searchTarget);
    }
    private void Render(List<MovieCard> movies)
    {
        Cards.Children.Clear(); Welcome.Visibility = movies.Count==0?Visibility.Visible:Visibility.Collapsed; Results.Visibility = Visibility.Visible; Browser.Visibility = Visibility.Hidden;
        ClearCollection.Visibility=collection=="results"?Visibility.Collapsed:Visibility.Visible;
        EmptyHeading.Text=collection=="history"?"Historia jest pusta":collection=="favorites"?"Twoje ulubione filmy":"Co dziś oglądamy?";
        EmptyDescription.Text=collection=="history"?"Tutaj pojawią się ostatnio otwierane filmy.":collection=="favorites"?"Dodaj film do ulubionych przyciskiem ☆ przy kafelku lub ★ w odtwarzaczu.":"Wybierz serwis i wpisz tytuł. Wyniki pojawią się jako miniaturki.";
        EmptyHint.Visibility=collection=="results"?Visibility.Visible:Visibility.Collapsed;
        foreach (var movie in movies)
        {
            var stack = new StackPanel { Width = 238 };
            var art = new Grid { Height = 134, Background = new SolidColorBrush(Color.FromRgb(24, 47, 58)) };
            art.Children.Add(new TextBlock { Text = "▶", FontSize = 40, Foreground = new SolidColorBrush(Color.FromRgb(121,223,199)), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center });
            if (Policy.Thumbnail(movie.thumbnail))
            {
                try { var image = new BitmapImage(); image.BeginInit(); image.UriSource = new Uri(movie.thumbnail); image.DecodePixelWidth = 480; image.EndInit(); art.Children.Add(new Image { Source = image, Stretch = Stretch.UniformToFill }); }
                catch (Exception error) { Log("thumbnail." + error.GetType().Name); }
            }
            stack.Children.Add(art);
            stack.Children.Add(new TextBlock { Text = movie.title, TextWrapping = TextWrapping.Wrap, MaxHeight = 63, FontSize = 16, Margin = new Thickness(12,12,12,6), FontWeight = FontWeights.SemiBold });
            stack.Children.Add(new TextBlock { Text = Policy.Site(movie.url) == Source.Vider ? "VIDER  ·  Otwórz film" : "FREEDISC  ·  Otwórz film", FontSize = 11, Foreground = Brushes.LightSeaGreen, Margin = new Thickness(12,4,12,12) });
            var card = new Button { Content = stack, Padding = new Thickness(0), Margin = new Thickness(6), VerticalContentAlignment = VerticalAlignment.Top, ToolTip = movie.title };
            card.Click += (_, _) => OpenMovie(movie.url,movie);
            var saved=collections?.Find(movie.url);var resume=MovieCollections.ResumePosition(saved);
            if(collection=="history"&&resume>0)stack.Children.Add(new TextBlock{Text="Wznów od "+TimeSpan.FromSeconds(resume).ToString(@"hh\:mm\:ss"),Foreground=Brushes.LightSeaGreen,Margin=new Thickness(12,0,12,10)});
            var tile=new StackPanel { Width=250,Margin=new Thickness(2) };tile.Children.Add(card);
            var actions=new WrapPanel();
            var star=new Button { Content=collections?.Favorite(movie.url)==true?"★ Usuń":"☆ Ulubione",Padding=new Thickness(10,7,10,7),FontSize=12 };
            star.Click+=(_,_)=>{if(CollectionAction(()=>collections?.Toggle(movie)))RefreshCollection();};actions.Children.Add(star);
            if(collection!="results")
            {
                var remove=new Button { Content=collection=="history"?"Usuń z historii":"Usuń z listy",Padding=new Thickness(10,7,10,7),FontSize=12 };
                remove.Click+=(_,_)=>{if(CollectionAction(()=>collections?.Remove(collection,movie.url)))RefreshCollection();};actions.Children.Add(remove);
            }
            tile.Children.Add(actions);Cards.Children.Add(tile);
        }
    }
    private async void OpenMovie(string url,MovieCard? card=null)
    {
        if (!ready || !Policy.Movie(url)) { Status.Text = "Wklej prawidłowy link do filmu Vider lub Freedisc."; return; }
        var sequence=++openSequence;await FlushPlayback();if(sequence!=openSequence||closed)return;
        url=Policy.MovieUrl(url);
        currentMovie=(card??collections?.Find(url)?.Card??new MovieCard("",url,"")) with {url=url};
        ResetPlayback();
        CollectionAction(()=>collections?.Opened(currentMovie));
        LeaveFullscreen(); ++epoch; mode = Mode.Movie; searching = false; movieTarget = url; retryUsed = false; returnPending = false; presentation.Navigation();
        PlayerTools.Visibility = Visibility.Visible; ShowBrowser(); Status.Text = "Otwieranie filmu…";
        Browser.CoreWebView2.Navigate(url);
    }
    private void ShowBrowser() { Results.Visibility = Visibility.Collapsed; Browser.Visibility = Visibility.Visible; }
    private async Task FocusMovie(bool automatic, int current)
    {
        if (!smoke && (!Policy.Movie(Browser.CoreWebView2.Source) || mode != Mode.Movie)) return;
        var result = await Eval("window.__vpEnsurePlayerFocus ? window.__vpEnsurePlayerFocus() : " + Script("focus-player.js"));
        Log("focus.result."+(result??"null")+".epoch."+current+"."+epoch);
        if (closed || current != epoch) return;
        if (result == "player")
        {
            if (automatic) presentation.Entered(); else presentation.User();
            EnterFullscreen();
            await SetFilmLayout(true);
            Status.Text = "Widok filmu · Esc: przyciski · F11: pełny ekran";
        }
        else if (!automatic) Status.Text = "Nie wykryto odtwarzacza. Uruchom film na stronie i spróbuj ponownie.";
    }
    private void EnterFullscreen()
    {
        if (fullscreen) return;
        ShowBrowser(); fullscreen = true; savedState = WindowState; savedStyle = WindowStyle; savedResize = ResizeMode;
        WindowState = WindowState.Normal; WindowStyle = WindowStyle.None; ResizeMode = ResizeMode.NoResize; WindowState = WindowState.Maximized;
        Header.Visibility = SearchBar.Visibility = CollectionsBar.Visibility = PlayerTools.Visibility = Footer.Visibility = Visibility.Collapsed;
        Browser.Focus();
    }
    private void ExitFullscreen()
    {
        if (!fullscreen) return;
        fullscreen = false; WindowState = WindowState.Normal; WindowStyle = savedStyle; ResizeMode = savedResize; WindowState = savedState;
        Header.Visibility = SearchBar.Visibility = CollectionsBar.Visibility = Footer.Visibility = Visibility.Visible;
        PlayerTools.Visibility = mode == Mode.Movie ? Visibility.Visible : Visibility.Collapsed;
    }
    private void LeaveFullscreen()
    {
        presentation.User(); ExitFullscreen();
        if (ready) _ = ExitSiteFullscreen();
    }
    private async Task ExitSiteFullscreen()
    {
        try { await Browser.ExecuteScriptAsync("if(document.fullscreenElement)document.exitFullscreen().catch(()=>{});"); }
        catch (Exception error) { Log("fullscreen.exit." + error.GetType().Name); }
    }
    private void ToggleFullscreen() { if (fullscreen) LeaveFullscreen(); else { presentation.User(); EnterFullscreen(); } }
    private void SearchClick(object sender, RoutedEventArgs e) => Search();
    private void QueryKeyDown(object sender, KeyEventArgs e) { if (e.Key == Key.Enter) { Search(); e.Handled = true; } }
    private void OpenLinkClick(object sender, RoutedEventArgs e) => OpenMovie(Query.Text.Trim());
    private async void SourceChanged(object sender, SelectionChangedEventArgs e)
    {
        source = SourcePicker.SelectedIndex == 1 ? Source.Freedisc : Source.Vider;
        if (!ready) return;
        await FlushPlayback();
        episodeOffer=false;episodeTimer?.Stop();episodeWindow?.Close();episodeWindow=null;
        ++epoch; mode = Mode.Results; searching = false; movieTarget = null; LeaveFullscreen(); Browser.CoreWebView2.Stop(); Browser.CoreWebView2.Navigate("about:blank");
        Browser.Visibility = Visibility.Hidden; Results.Visibility = Visibility.Visible; PlayerTools.Visibility = Visibility.Collapsed; Cards.Children.Clear(); Welcome.Visibility = Visibility.Visible;collection="results";searchCards=[];ClearCollection.Visibility=Visibility.Collapsed;
        Status.Text = "Wybrano " + (source == Source.Vider ? "Vider" : "Freedisc") + ". Wpisz tytuł i wybierz Szukaj.";
    }
    private bool CollectionAction(Action action)
    {
        if(collections==null){Status.Text="Lokalne kolekcje są niedostępne. Sprawdź uprawnienia do folderu aplikacji.";return false;}
        try{action();return true;}
        catch(Exception error) when(error is IOException or UnauthorizedAccessException)
        {Log("collections.write."+error.GetType().Name);Status.Text="Nie udało się zapisać zmian kolekcji na dysku.";return false;}
    }
    private async Task UpdateMetadata(string url,int current)
    {
        if(!Policy.Movie(url)||collections==null)return;
        try
        {
            var raw=await Eval("(function(){const h=document.querySelector('h1');const m=document.querySelector('meta[property=\"og:image\"],link[rel=\"image_src\"]');return JSON.stringify({title:h?h.textContent:document.title,thumbnail:m?(m.content||m.href||''):''});}())");
            if(closed||epoch!=current||!Policy.Movie(Browser.CoreWebView2.Source)||MovieCollections.Key(url)!=MovieCollections.Key(Browser.CoreWebView2.Source))return;
            using var data=JsonDocument.Parse(raw??"{}");
            string title=data.RootElement.TryGetProperty("title",out var t)?t.GetString()??"":"";
            string thumbnail=data.RootElement.TryGetProperty("thumbnail",out var thumb)?thumb.GetString()??"":"";
            CollectionAction(()=>collections.Metadata(new(title,url,thumbnail)));
            currentMovie=collections.Find(url)?.Card??currentMovie;
        }
        catch(Exception error){Log("collections.metadata."+error.GetType().Name);}
    }
    private void RefreshCollection()
    {
        var list=collection=="results"?searchCards:collections?.Items(collection).Select(i=>i.Card).ToList()??[];
        Render(list);
        Status.Text=(collection=="history"?"Historia":collection=="favorites"?"Ulubione":"Wyniki")+" · "+list.Count+(list.Count==0?" · lista jest pusta":" · kliknij miniaturkę, aby otworzyć film");
    }
    private async void ShowCollection(string selected)
    {
        await FlushPlayback();
        episodeOffer=false;episodeTimer?.Stop();episodeWindow?.Close();episodeWindow=null;
        ++epoch; LeaveFullscreen();mode=Mode.Results;searching=false;
        if(ready){Browser.CoreWebView2.Stop();Browser.CoreWebView2.Navigate("about:blank");}
        collection=selected;PlayerTools.Visibility=Visibility.Collapsed;RefreshCollection();
    }
    private void ResultsClick(object sender,RoutedEventArgs e)=>ShowCollection("results");
    private void HistoryClick(object sender,RoutedEventArgs e)=>ShowCollection("history");
    private void FavoritesClick(object sender,RoutedEventArgs e)=>ShowCollection("favorites");
    private void ClearCollectionClick(object sender,RoutedEventArgs e)
    {
        if(collection=="results")return;
        if(MessageBox.Show(this,collection=="history"?"Wyczyścić historię na tym komputerze?":"Wyczyścić ulubione na tym komputerze?","Usuń pozycje",MessageBoxButton.YesNo,MessageBoxImage.Question)!=MessageBoxResult.Yes)return;
        if(CollectionAction(()=>collections?.Clear(collection)))RefreshCollection();
    }
    private void FavoriteMovieClick(object sender,RoutedEventArgs e)
    {
        if(currentMovie==null)return;
        if(CollectionAction(()=>collections?.Toggle(currentMovie)))Status.Text=collections?.Favorite(currentMovie.url)==true?"Dodano do ulubionych.":"Usunięto z ulubionych.";
    }
    private async void ReloadClick(object sender, RoutedEventArgs e) { await FlushPlayback();presentation.Navigation(); Browser.Reload(); }
    private async void FocusClick(object sender, RoutedEventArgs e) { try { await FocusMovie(false, epoch); } catch (Exception error) { Log("focus." + error.GetType().Name); } }
    private async void PageClick(object sender, RoutedEventArgs e)
    {
        LeaveFullscreen(); ShowBrowser();
        await SetFilmLayout(false);
        try { await Eval("(function(){if(!document.getElementById('vp-focus-style'))return 'page';return " + Script("focus-player.js") + "}())"); } catch (Exception error) { Log("page." + error.GetType().Name); }
    }
    private void FullscreenClick(object sender, RoutedEventArgs e) => ToggleFullscreen();
    private void ReturnClick(object sender, RoutedEventArgs e) { if (movieTarget != null) OpenMovie(movieTarget); }
    private void ShowPageClick(object sender, RoutedEventArgs e) { if (ready) ShowBrowser(); }
    private void CheckClick(object sender, RoutedEventArgs e) { if ((googleSearch || searchTarget != null) && mode == Mode.Search) { searching = true; searchTicks = 0; searchDeadline = DateTime.UtcNow.AddSeconds(40); Status.Text = "Ponowny odczyt wyników…"; } }
    private async void ReportClick(object sender, RoutedEventArgs e)
    {
        await FlushPlayback();
        var dialog = new SaveFileDialog { FileName = "VideoPlayer-raport.txt", Filter = "Raport tekstowy|*.txt" };
        if (dialog.ShowDialog(this) != true) return;
        try { File.WriteAllLines(dialog.FileName, new[] { "VideoPlayer Windows 0.3.6", Environment.OSVersion.ToString() }.Concat(RuntimeSnapshot()).Concat(log)); Status.Text = "Raport zapisany. Nie zawiera kodów, cookies ani wpisanych tytułów."; }
        catch (IOException) { Status.Text = "Nie udało się zapisać raportu w wybranym miejscu."; }
    }
    private static void WriteSmoke(string text) => File.WriteAllText(Path.Combine(AppContext.BaseDirectory, "smoke-result.txt"), text);
    private async Task SmokeTest()
    {
        timer.Stop(); mode = Mode.Movie; ShowBrowser();
        var completion = new TaskCompletionSource();
        const string fixtureUrl="https://vider.info/vid/+fdesktoptest";
        var html="<!doctype html><html><body data-vp-smoke='ready'><div class='video-frame'><video controls></video></div><p id='comments'>Comments</p></body></html>";
        Browser.CoreWebView2.AddWebResourceRequestedFilter(fixtureUrl,CoreWebView2WebResourceContext.Document);
        Browser.CoreWebView2.WebResourceRequested+=(_,args)=>
        {
            if(args.Request.Uri==fixtureUrl)args.Response=Browser.CoreWebView2.Environment.CreateWebResourceResponse(new MemoryStream(System.Text.Encoding.UTF8.GetBytes(html)),200,"OK","Content-Type: text/html; charset=utf-8");
        };
        EventHandler<CoreWebView2NavigationCompletedEventArgs> handler = (_,args) =>
        {if(args.IsSuccess)completion.TrySetResult();else completion.TrySetException(new Exception("Fixture navigation failed: "+args.WebErrorStatus));};
        Browser.CoreWebView2.NavigationCompleted += handler;
        Browser.CoreWebView2.Navigate(fixtureUrl);
        await completion.Task.WaitAsync(TimeSpan.FromSeconds(20));
        Browser.CoreWebView2.NavigationCompleted -= handler;
        var deadline=DateTime.UtcNow.AddSeconds(20);
        while(await Browser.ExecuteScriptAsync("!!document.querySelector('body[data-vp-smoke=ready] .video-frame video')")!="true")
        {if(DateTime.UtcNow>deadline)throw new Exception("Local smoke document not ready: "+Browser.CoreWebView2.Source);await Task.Delay(100);}
        await FocusMovie(true, epoch);
        if (!fullscreen || Header.Visibility != Visibility.Collapsed) throw new Exception("Automatic native fullscreen failed: full="+fullscreen+" header="+Header.Visibility+" source="+Browser.CoreWebView2.Source+" focus="+await Browser.ExecuteScriptAsync("!!document.getElementById('vp-focus-style')")+" log="+string.Join(" | ",log));
        if (await Browser.ExecuteScriptAsync("!!document.getElementById('vp-focus-style') && document.querySelector('video').paused") != "true") throw new Exception("Focus or no-autoplay failed");
        LeaveFullscreen();
        if (fullscreen || presentation.Pending || Header.Visibility != Visibility.Visible) throw new Exception("Escape did not restore controls");
        await Eval("(function(){if(!document.getElementById('vp-focus-style'))return 'page';return " + Script("focus-player.js") + "}())");
        if (await Browser.ExecuteScriptAsync("!!document.getElementById('vp-focus-style')") != "false") throw new Exception("Page restore failed");
        var state = await Eval("document.body.insertAdjacentHTML('beforeend', '<input name=\"captcha_code\" id=\"challenge\">');" + Script("search-state.js"));
        if (state == null || !state.Contains("verification")) throw new Exception("Challenge detection failed");
        await Browser.ExecuteScriptAsync("document.getElementById('challenge').remove();document.body.insertAdjacentHTML('beforeend', '<a href=\"https://vider.info/vid/+fabc123\" title=\"Test movie\">Test</a>');");
        var cards = JsonSerializer.Deserialize<List<MovieCard>>(await Eval(Script("search-results.js")) ?? "[]");
        if (cards?.Count != 1 || cards[0].title != "Test movie") throw new Exception("Search extraction failed");
        Render(cards);
        if (Cards.Children.Count != 1) throw new Exception("Native cards failed");
        CollectionAction(()=>collections?.Clear("history"));
        CollectionAction(()=>collections?.Opened(cards[0]));
        CollectionAction(()=>collections?.Toggle(cards[0]));
        ShowCollection("history");if(Cards.Children.Count!=1)throw new Exception("History tiles failed");
        CollectionAction(()=>collections?.Clear("history"));RefreshCollection();if(Cards.Children.Count!=0)throw new Exception("History clear failed");
        ShowCollection("favorites");if(Cards.Children.Count!=1)throw new Exception("Favorites independent from history failed");
        CollectionAction(()=>collections?.Remove("favorites",cards[0].url));RefreshCollection();if(Cards.Children.Count!=0)throw new Exception("Favorite removal failed");
        // Exercise the real document-created WebView2 bridge with the site's JW event contract.
        mode=Mode.Movie;OpenMovie(fixtureUrl);
        await Task.Delay(1500);
        await Browser.ExecuteScriptAsync("window.smokePlayer={state:'paused',events:{},getState(){return this.state},on(n,f){this.events[n]=f},play(){this.state='playing'},pause(){this.state='paused'},seek(p){this.position=p},getPosition(){return this.position||0}};window.jwplayer=()=>window.smokePlayer;");
        var bridgeDeadline=DateTime.UtcNow.AddSeconds(8);
        while(endpoints.Count==0){if(DateTime.UtcNow>bridgeDeadline)throw new Exception("Playback bridge handshake failed");await Task.Delay(100);}
        await Browser.ExecuteScriptAsync("window.smokePlayer.position=123;window.smokePlayer.events.time({position:123,duration:7200})");
        await Task.Delay(300);
        if(collections?.Find(fixtureUrl)?.Position!=123)throw new Exception("Playback progress persistence failed");
        MediaCommand("play");await Task.Delay(200);
        if(await Browser.ExecuteScriptAsync("window.smokePlayer.state")!="\"playing\"")throw new Exception("Play command bridge failed");
        MediaCommand("pause");await Task.Delay(200);
        if(await Browser.ExecuteScriptAsync("window.smokePlayer.state")!="\"paused\"")throw new Exception("Pause command bridge failed");
        // A cross-origin embedded player that starts only after a trusted original-control click.
        const string embedRoot="https://freedisc.pl/smoke,f-12345,film-mp4";
        const string embedFixture="https://stream.freedisc.pl/embed/video/12345";
        const string outerFixture="https://ovh.freedisc.pl/embed/video/777777";
        var embedHtml="""
        <!doctype html><html><body style="margin:0"><header id="embed_hover_header" style="height:30px;position:fixed;top:0;z-index:9999">Login and rating</header><div class="jwplayer" id="embedded-player" style="position:relative;width:740px;height:390px;background:#222"><video></video><div class="jw-display-icon-display" role="button" style="position:absolute;left:40%;top:40%;width:120px;height:70px;background:white;color:black" onclick="if(event.isTrusted){window.smokePlayer.state='playing';window.smokePlayer.ready=true;window.smokePlayer.position=321;window.smokePlayer.events.time({position:321,duration:7200})}">Play</div></div><script>window.smokePlayer={state:'paused',events:{},getState(){return this.state},on(n,f){this.events[n]=f},play(){},pause(){this.state='paused'},seek(p){this.requested=p;if(this.state==='playing'){this.position=p;this.events.time({position:p,duration:7200})}},getDuration(){return 7200},getPosition(){return this.position||0}};window.jwplayer=()=>window.smokePlayer;Object.defineProperty(document.querySelector('video'),'readyState',{get:()=>window.smokePlayer.ready?1:0});</script></body></html>
        """;
        foreach(var fixture in new[]{embedRoot,outerFixture,embedFixture})Browser.CoreWebView2.AddWebResourceRequestedFilter(fixture,CoreWebView2WebResourceContext.Document);
        Browser.CoreWebView2.WebResourceRequested+=(_,args)=>{
            string? markup=args.Request.Uri==embedRoot?"<!doctype html><div class='video-frame' style='width:740px;height:390px;transform:translateY(30px);overflow:hidden'><iframe id='lazy' style='width:100%;height:390px;border:0'></iframe></div><script>document.getElementById('lazy').contentWindow.location.replace('"+outerFixture+"');</script>":args.Request.Uri==outerFixture?"<!doctype html><div style='width:740px;height:390px;transform:translateY(30px)'><iframe id='inner' style='width:740px;height:390px;border:0'></iframe></div><script>document.getElementById('inner').contentWindow.location.replace('"+embedFixture+"');</script>":args.Request.Uri==embedFixture?embedHtml:null;
            if(markup!=null)args.Response=Browser.CoreWebView2.Environment.CreateWebResourceResponse(new MemoryStream(System.Text.Encoding.UTF8.GetBytes(markup)),200,"OK","Content-Type: text/html; charset=utf-8");
        };
        CoreWebView2Frame? embeddedFrame=null;
        Browser.CoreWebView2.FrameCreated+=(_,args)=>embeddedFrame=args.Frame;
        OpenMovie(embedRoot);
        var embedDeadline=DateTime.UtcNow.AddSeconds(15);
        while(!endpoints.ContainsKey(embedFixture)){if(DateTime.UtcNow>embedDeadline)throw new Exception("Embedded playback handshake failed: "+string.Join(" | ",log));await Task.Delay(100);}
        embeddedFrame=playbackFrames[embedFixture];
        if(!playerDepths.TryGetValue(embedFixture,out var nestedDepth)||nestedDepth<2)throw new Exception("Deeply nested player was not registered");
        await FocusMovie(true,epoch);await Task.Delay(300);
        const string layoutCheck="(function(){const p=document.querySelector('.jwplayer'),r=p.getBoundingClientRect();return Math.abs(r.x)<2&&Math.abs(r.y)<2&&Math.abs(r.width-innerWidth)<2&&Math.abs(r.height-innerHeight)<2&&getComputedStyle(p.querySelector('video')).objectFit==='contain'&&getComputedStyle(document.getElementById('embed_hover_header')).display==='none';}())";
        if(embeddedFrame==null||await embeddedFrame.ExecuteScriptAsync(layoutCheck)!="true")throw new Exception("Freedisc delayed-frame player does not fill viewport: "+string.Join(" | ",log));
        if(await embeddedFrame.ExecuteScriptAsync("!!document.getElementById('vp-frame-fit-style')")!="false")throw new Exception("Fixture unexpectedly used legacy src relay");
        if(await Browser.ExecuteScriptAsync("(function(){const r=document.querySelector('iframe').getBoundingClientRect();return Math.abs(r.x)<2&&Math.abs(r.y)<2&&Math.abs(r.width-innerWidth)<2&&Math.abs(r.height-innerHeight)<2;})()")!="true")throw new Exception("Freedisc outer iframe did not fill window");
        LeaveFullscreen();Width=950;Height=650;await Task.Delay(400);
        if(await embeddedFrame.ExecuteScriptAsync(layoutCheck)!="true")throw new Exception("Embedded player did not follow window resize");
        await FocusMovie(false,epoch);await Task.Delay(300);
        if(await embeddedFrame.ExecuteScriptAsync(layoutCheck)!="true")throw new Exception("Embedded player did not fill fullscreen after resize");
        MediaCommand("play");
        while(collections?.Find(embedRoot)?.Position!=321){if(DateTime.UtcNow>embedDeadline)throw new Exception("Trusted embedded Play click failed: "+string.Join(" | ",log));await Task.Delay(100);}
        // Replayed/unsolicited coordinates must never create a click.
        int clickCount=log.Count(l=>l.Contains("media.original_play_click"));
        using(var rejected=JsonDocument.Parse("{\"id\":\"unsolicited\",\"x\":0.5,\"y\":0.5}"))await TrustedPlayTap(embedRoot,rejected.RootElement);
        if(log.Count(l=>l.Contains("media.original_play_click"))!=clickCount)throw new Exception("Unsolicited click was accepted");
        await SmokeResume(embedRoot,embedFixture,embeddedFrame);
        embeddedFrame=playbackFrames[embedFixture];
        await SmokeEpisodes(embeddedFrame);
        const string googleFixture="https://www.google.com/search?hl=pl&q=smoke";
        Browser.CoreWebView2.AddWebResourceRequestedFilter(googleFixture,CoreWebView2WebResourceContext.Document);
        Browser.CoreWebView2.WebResourceRequested+=(_,args)=>{if(args.Request.Uri==googleFixture)args.Response=Browser.CoreWebView2.Environment.CreateWebResourceResponse(new MemoryStream(System.Text.Encoding.UTF8.GetBytes("<!doctype html><a href='https://freedisc.pl/embed/video/8307530?match=1'><h3>Ronin 1998</h3></a><a href='https://evil.test/movie'><h3>External</h3></a>")),200,"OK","Content-Type: text/html; charset=utf-8");};
        LeaveFullscreen();mode=Mode.Search;googleSearch=true;searching=true;source=Source.Freedisc;
        var googleComplete=new TaskCompletionSource();
        EventHandler<CoreWebView2NavigationCompletedEventArgs> googleHandler=(_,args)=>{if(args.IsSuccess)googleComplete.TrySetResult();else googleComplete.TrySetException(new Exception("Google fixture failed"));};
        Browser.CoreWebView2.NavigationCompleted+=googleHandler;Browser.CoreWebView2.Navigate(googleFixture);await googleComplete.Task.WaitAsync(TimeSpan.FromSeconds(15));Browser.CoreWebView2.NavigationCompleted-=googleHandler;
        await ReadGoogle(epoch);
        if(searchCards.Count!=1||!searchCards[0].url.EndsWith("?match=1")||Cards.Children.Count!=1)throw new Exception("Scoped Google embed tiles failed");
        WriteSmoke("PASS: WebView2 startup, focus/fullscreen, manual exit, CAPTCHA detection, native cards, history/favorites deletion, playback bridge handshake, persisted progress, play/pause commands, trusted cross-origin original Play click, two-level cross-origin Freedisc frame tracking, delayed-src native frame fit, outer/inner viewport fill/resize/aspect ratio, unsolicited-click rejection, history flush/reopen/actual delayed seek/pause save, episode prefetch/countdown/cards/no-match/verification/error/unrecognized UI, scoped Google embed tiles.");
        Application.Current.Shutdown(0);
    }
}

