using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Wpf;
namespace VideoPlayer.Windows;
public partial class MainWindow
{
    private EpisodePlan? nextEpisode;
    private WebView2? episodeBrowser;
    private DispatcherTimer? episodeTimer;
    private bool episodeVerification,episodeDocumentReady;
    private bool episodeBusy,episodeGoogle,episodeRouted,episodeFallback,episodeAccess,episodeOffer,episodeDone;
    private Source episodeSource;
    private string episodeQuery="";
    private DateTime episodeDeadline;
    private List<MovieCard> episodeCards=[];
    private int episodeEpoch;
    private void ConsiderEpisode(double position,double duration)
    {
        if(duration<60||position<0||position>duration+2)return;
        if(nextEpisode==null&&duration-position<=40)
        {
            var current=EpisodePlan.Parse(currentMovie?.title);if(current==null)return;
            nextEpisode=current.Next();episodeSource=Policy.Site(currentMovie!.url);episodeFallback=false;episodeOffer=false;episodeDone=false;episodeCards=[];
            _=BeginEpisodeSearch();
        }
        if(nextEpisode!=null&&duration-position<=10)
        {episodeOffer=true;ShowEpisodeOffer();}
    }
    private async Task BeginEpisodeSearch()
    {
        var current=epoch;episodeEpoch=current;episodeGoogle=false;episodeRouted=false;episodeAccess=false;episodeDone=false;
        episodeQuery=nextEpisode!.Query;episodeDeadline=DateTime.UtcNow.AddSeconds(35);
        try
        {
            if(episodeBrowser==null)
            {
                episodeBrowser=new WebView2{Visibility=Visibility.Hidden,DefaultBackgroundColor=System.Drawing.Color.White};
                Workspace.Children.Insert(0,episodeBrowser);
                await episodeBrowser.EnsureCoreWebView2Async(Browser.CoreWebView2.Environment);
                var c=episodeBrowser.CoreWebView2;c.Settings.AreHostObjectsAllowed=false;c.Settings.IsWebMessageEnabled=false;c.Settings.AreDevToolsEnabled=false;
                c.NavigationCompleted+=(_,e)=>episodeDocumentReady=e.IsSuccess;
                c.NavigationStarting+=(_,e)=>{episodeDocumentReady=false;episodeAccess=false;if(!(episodeGoogle?Policy.Google(e.Uri):Policy.Allowed(e.Uri)&&Policy.Site(e.Uri)==episodeSource))e.Cancel=true;};
                c.NewWindowRequested+=(_,e)=>e.Handled=true;c.DownloadStarting+=(_,e)=>e.Cancel=true;c.PermissionRequested+=(_,e)=>e.State=CoreWebView2PermissionState.Deny;
                episodeTimer=new DispatcherTimer{Interval=TimeSpan.FromSeconds(1)};episodeTimer.Tick+=async(_,_)=>await InspectEpisode();
            }
            if(current!=epoch||closed)return;
            episodeBrowser.CoreWebView2.Navigate(episodeSource==Source.Freedisc?Policy.Home(episodeSource):Policy.Search(episodeSource,episodeQuery));episodeTimer!.Start();
        }
        catch(Exception error){Log("episode.start."+error.GetType().Name);episodeDone=true;}
    }
    private async Task<string?> EpisodeEval(string js)
    {var raw=await episodeBrowser!.ExecuteScriptAsync(js).WaitAsync(TimeSpan.FromSeconds(8));return raw is "null" or "undefined"?null:JsonSerializer.Deserialize<string>(raw);}
    private async Task InspectEpisode()
    {
        if(episodeBusy||episodeDone||closed||episodeEpoch!=epoch||nextEpisode==null){if(closed||episodeEpoch!=epoch)episodeTimer?.Stop();return;}
        episodeBusy=true;var current=epoch;
        try
        {
            if(DateTime.UtcNow>episodeDeadline&&!episodeAccess)
            {
                if(!episodeGoogle){episodeGoogle=true;episodeDeadline=DateTime.UtcNow.AddSeconds(35);episodeBrowser!.CoreWebView2.Navigate(Policy.GoogleSearch(episodeSource,episodeQuery));}
                else{episodeDone=true;episodeTimer?.Stop();if(episodeOffer)RefreshEpisodeOffer();}
                return;
            }
            var url=episodeBrowser!.CoreWebView2.Source;
            if(!episodeDocumentReady)return;
            if(!(episodeGoogle?Policy.Google(url):Policy.Allowed(url)))return;
            List<MovieCard> rows;bool complete=false;
            if(episodeGoogle)
            {
                using var doc=JsonDocument.Parse(await EpisodeEval(Script("google-results.js").Replace("__VP_SOURCE__",JsonSerializer.Serialize(episodeSource==Source.Freedisc?"FREEDISC":"VIDER")))??"{}");
                var d=doc.RootElement;episodeAccess=d.TryGetProperty("stage",out var stage)&&stage.GetString()=="access";
                complete=stage.ValueKind==JsonValueKind.String&&stage.GetString()=="complete";
                rows=d.TryGetProperty("rows",out var r)?JsonSerializer.Deserialize<List<MovieCard>>(r.GetRawText())??[]:[];
            }
            else
            {
                using var doc=JsonDocument.Parse(await EpisodeEval(Script("search-state.js"))??"{}");
                episodeAccess=doc.RootElement.TryGetProperty("stage",out var stage)&&stage.GetString()!="results";
                if(!episodeAccess&&episodeSource==Source.Freedisc&&!episodeRouted)
                    episodeRouted=await EpisodeEval(Script("freedisc-search.js").Replace("__VP_SEARCH_URL__",JsonSerializer.Serialize(Policy.Search(episodeSource,episodeQuery))))=="routed";
                if(!episodeAccess&&!SearchDocumentMatches(episodeBrowser.CoreWebView2.Source,episodeSource,episodeQuery))return;
                rows=JsonSerializer.Deserialize<List<MovieCard>>(await EpisodeEval(Script("search-results.js"))??"[]")??[];
                using var outcome=JsonDocument.Parse(await EpisodeEval(Script("search-outcome.js"))??"{}");
                complete=outcome.RootElement.TryGetProperty("empty",out var empty)&&empty.GetBoolean();
            }
            if(current!=epoch||closed||nextEpisode==null)return;
            if(episodeAccess){episodeDeadline=DateTime.UtcNow.AddSeconds(35);if(episodeOffer)RefreshEpisodeOffer();return;}
            var valid=rows.Where(m=>Policy.Movie(m.url)&&Policy.Site(m.url)==episodeSource).ToList();
            var matching=valid.Where(m=>nextEpisode.Matches(m.title)).Take(6).ToList();
            if(matching.Count>0){episodeCards=matching;episodeDone=true;episodeTimer?.Stop();if(episodeOffer)RefreshEpisodeOffer();return;}
            if(complete||valid.Count>0)
            {
                if(!episodeFallback){episodeFallback=true;nextEpisode=EpisodePlan.Parse(currentMovie?.title)?.NextSeason();if(nextEpisode!=null){await BeginEpisodeSearch();return;}}
                episodeDone=true;episodeTimer?.Stop();if(episodeOffer)RefreshEpisodeOffer();return;
            }
            if(DateTime.UtcNow>episodeDeadline)
            {
                if(!episodeGoogle){episodeGoogle=true;episodeDeadline=DateTime.UtcNow.AddSeconds(35);episodeBrowser.CoreWebView2.Navigate(Policy.GoogleSearch(episodeSource,episodeQuery));}
                else{episodeDone=true;episodeTimer?.Stop();if(episodeOffer)ShowEpisodeOffer();}
            }
        }
        catch(Exception error){Log("episode.inspect."+error.GetType().Name);if(DateTime.UtcNow>episodeDeadline){episodeDone=true;episodeTimer?.Stop();}}
        finally{episodeBusy=false;}
    }
    private void RefreshEpisodeOffer()
    {
        if(episodeVerification)return;
        episodeWindow?.Close();episodeWindow=null;ShowEpisodeOffer();
    }
    private void ShowEpisodeOffer()
    {
        if(episodeWindow!=null||!episodeOffer||nextEpisode==null)return;
        // An owned native window stays above WebView2's HWND without replacing or pausing the movie.
        var panel=new StackPanel{Margin=new Thickness(12)};
        panel.Children.Add(new TextBlock{Text="Następny odcinek · "+nextEpisode.Query,FontSize=16,Foreground=Brushes.White});
        if(episodeCards.Count>0)
        {
            var row=new WrapPanel();
            foreach(var movie in episodeCards)
            {
                var content=new StackPanel{Width=150};
                if(Policy.Thumbnail(movie.thumbnail))try{content.Children.Add(new Image{Height=84,Stretch=Stretch.UniformToFill,Source=new System.Windows.Media.Imaging.BitmapImage(new Uri(movie.thumbnail))});}catch{}
                content.Children.Add(new TextBlock{Text=movie.title,TextWrapping=TextWrapping.Wrap,MaxHeight=48});
                var button=new Button{Content=content,Margin=new Thickness(4),Padding=new Thickness(5)};button.Click+=(_,_)=>OpenMovie(movie.url,movie);row.Children.Add(button);
            }
            panel.Children.Add(row);
        }
        else
        {
            var text=episodeAccess?"Potwierdź dostęp, aby dokończyć wyszukiwanie":episodeDone?"Nie znaleziono odcinka lub wyszukiwanie jest niedostępne":"Wyszukuję kolejny odcinek…";
            var button=new Button{Content=text,Margin=new Thickness(0,12,0,0)};
            button.Click+=(_,_)=>{if(episodeAccess)OpenEpisodeVerification();else if(episodeDone){episodeWindow?.Close();episodeWindow=null;_=BeginEpisodeSearch();}};panel.Children.Add(button);
        }
        var dismiss=new Button{Content="Zamknij",HorizontalAlignment=HorizontalAlignment.Right};dismiss.Click+=(_,_)=>{episodeWindow?.Close();episodeWindow=null;episodeOffer=false;};panel.Children.Add(dismiss);
        episodeWindow=new Window{Owner=this,Title="Następny odcinek",Content=panel,Width=Math.Min(1040,ActualWidth-40),SizeToContent=SizeToContent.Height,WindowStyle=WindowStyle.ToolWindow,ResizeMode=ResizeMode.NoResize,ShowInTaskbar=false,ShowActivated=false,Background=new SolidColorBrush(Color.FromRgb(20,39,49)),Foreground=Brushes.White,WindowStartupLocation=WindowStartupLocation.CenterOwner};
        episodeWindow.Show();
    }
    private void OpenEpisodeVerification()
    {
        if(episodeBrowser==null)return;
        episodeVerification=true;
        episodeWindow?.Close();episodeWindow=null;
        Workspace.Children.Remove(episodeBrowser);episodeBrowser.Visibility=Visibility.Visible;
        var host=new Window{Owner=this,Title="Potwierdź dostęp · wyszukiwanie następnego odcinka",Width=850,Height=650,Content=episodeBrowser,WindowStartupLocation=WindowStartupLocation.CenterOwner};
        host.Closed+=(_,_)=>{episodeVerification=false;host.Content=null;episodeBrowser.Visibility=Visibility.Hidden;Workspace.Children.Insert(0,episodeBrowser);episodeWindow=null;if(episodeOffer&&!closed)ShowEpisodeOffer();};
        episodeWindow=host;host.Show();
    }
}
