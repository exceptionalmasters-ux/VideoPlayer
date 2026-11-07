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
    private enum EpisodeStatus { NotStarted, Searching, Access, Found, NoMatch, Error, Unrecognized }
    private EpisodeStatus episodeStatus;
    private EpisodePlan? nextEpisode,episodeCurrent;
    private WebView2? episodeBrowser;
    private DispatcherTimer? episodeTimer;
    private bool episodeVerification,episodeDocumentReady,episodeBusy,episodeGoogle,episodeRouted,episodeFallback,episodeAccess,episodeOffer,episodeDone,episodeDismissed;
    private Source episodeSource;
    private string episodeQuery="",episodeOriginalTitle="";
    private DateTime episodeDeadline;
    private List<MovieCard> episodeCards=[];
    private int episodeEpoch,episodeGeneration;
    private ulong episodeNavigationId;
    private Task? episodeStartTask;
    private void ResetEpisodeState()
    {
        ++episodeGeneration;episodeStatus=EpisodeStatus.NotStarted;episodeCurrent=null;nextEpisode=null;
        episodeOriginalTitle=currentMovie?.title??"";episodeOffer=false;episodeDismissed=false;episodeDone=false;episodeAccess=false;episodeFallback=false;episodeCards=[];
    }
    private void ConsiderEpisode(double position,double duration,bool ended=false)
    {
        if(!double.IsFinite(position)||!double.IsFinite(duration)||duration<=0||duration>86400||position<0||position>duration+2)return;
        var remaining=ended?0:duration-position;
        if(episodeStatus==EpisodeStatus.NotStarted&&remaining<=40)
        {
            episodeCurrent=EpisodePlan.Resolve(currentMovie?.title,episodeOriginalTitle,currentMovie?.url);
            if(episodeCurrent==null){episodeStatus=EpisodeStatus.Unrecognized;episodeDone=true;Log("episode.unrecognized");}
            else
            {
                nextEpisode=episodeCurrent.Next();episodeSource=Policy.Site(currentMovie!.url);episodeFallback=false;
                Log("episode.prefetch");episodeStartTask=BeginEpisodeSearch();
            }
        }
        if(remaining<=10&&!episodeDismissed){episodeOffer=true;ShowEpisodeOffer();}
    }
    private async Task BeginEpisodeSearch()
    {
        if(nextEpisode==null||closed)return;
        int current=epoch,generation=++episodeGeneration;episodeEpoch=current;
        episodeGoogle=false;episodeRouted=false;episodeAccess=false;episodeDone=false;episodeStatus=EpisodeStatus.Searching;episodeCards=[];
        episodeQuery=nextEpisode.Query;episodeDeadline=DateTime.UtcNow.AddSeconds(35);RefreshEpisodeOffer();
        try
        {
            if(episodeBrowser==null)
            {
                episodeBrowser=new WebView2{Visibility=Visibility.Hidden,DefaultBackgroundColor=System.Drawing.Color.White};
                Workspace.Children.Insert(0,episodeBrowser);
                await episodeBrowser.EnsureCoreWebView2Async(Browser.CoreWebView2.Environment);
                var c=episodeBrowser.CoreWebView2;c.Settings.AreHostObjectsAllowed=false;c.Settings.IsWebMessageEnabled=false;c.Settings.AreDevToolsEnabled=false;
                c.NavigationCompleted+=(_,e)=>{if(e.NavigationId!=episodeNavigationId)return;episodeDocumentReady=e.IsSuccess;if(!e.IsSuccess)Log("episode.navigation."+e.WebErrorStatus);};
                c.NavigationStarting+=(_,e)=>{episodeNavigationId=e.NavigationId;episodeDocumentReady=false;episodeAccess=false;if(!(episodeGoogle?Policy.Google(e.Uri):Policy.Allowed(e.Uri)&&Policy.Site(e.Uri)==episodeSource))e.Cancel=true;};
                c.NewWindowRequested+=(_,e)=>e.Handled=true;c.DownloadStarting+=(_,e)=>e.Cancel=true;c.PermissionRequested+=(_,e)=>e.State=CoreWebView2PermissionState.Deny;
                episodeTimer=new DispatcherTimer{Interval=TimeSpan.FromSeconds(1)};episodeTimer.Tick+=async(_,_)=>await InspectEpisode();
                if(smoke)ConfigureEpisodeFixtures(c);
            }
            if(current!=epoch||generation!=episodeGeneration||closed)return;
            episodeBrowser.CoreWebView2.Stop();episodeDocumentReady=false;
            episodeBrowser.CoreWebView2.Navigate(episodeSource==Source.Freedisc?Policy.Home(episodeSource):Policy.Search(episodeSource,episodeQuery));episodeTimer!.Start();
        }
        catch(Exception error){Log("episode.start."+error.GetType().Name);if(current==epoch&&generation==episodeGeneration)FinishEpisode(EpisodeStatus.Error);}
    }
    private async Task<string?> EpisodeEval(string js)
    {var raw=await episodeBrowser!.ExecuteScriptAsync(js).WaitAsync(TimeSpan.FromSeconds(8));return raw is "null" or "undefined"?null:JsonSerializer.Deserialize<string>(raw);}
    private void FinishEpisode(EpisodeStatus result,List<MovieCard>? cards=null)
    {
        episodeStatus=result;episodeDone=true;episodeAccess=false;episodeCards=cards??[];episodeTimer?.Stop();
        Log("episode.result."+result);
        if(episodeVerification)episodeWindow?.Close();
        RefreshEpisodeOffer();
    }
    private void GoogleEpisodeSearch()
    {
        episodeGoogle=true;episodeRouted=false;episodeDocumentReady=false;episodeDeadline=DateTime.UtcNow.AddSeconds(35);
        Log("episode.google_fallback");episodeBrowser!.CoreWebView2.Navigate(Policy.GoogleSearch(episodeSource,episodeQuery));
    }
    private async Task InspectEpisode()
    {
        if(episodeBusy||episodeDone||closed||episodeEpoch!=epoch||nextEpisode==null){if(closed||episodeEpoch!=epoch)episodeTimer?.Stop();return;}
        episodeBusy=true;int current=epoch,generation=episodeGeneration;
        bool Stale()=>closed||current!=epoch||generation!=episodeGeneration;
        try
        {
            if(DateTime.UtcNow>episodeDeadline&&!episodeAccess)
            {if(!episodeGoogle)GoogleEpisodeSearch();else FinishEpisode(EpisodeStatus.Error);return;}
            var url=episodeBrowser!.CoreWebView2.Source;
            if(!episodeDocumentReady||!(episodeGoogle?Policy.Google(url):Policy.Allowed(url)))return;
            List<MovieCard> rows;bool complete=false,access=false;
            if(episodeGoogle)
            {
                using var doc=JsonDocument.Parse(await EpisodeEval(Script("google-results.js").Replace("__VP_SOURCE__",JsonSerializer.Serialize(episodeSource==Source.Freedisc?"FREEDISC":"VIDER")))??"{}");if(Stale())return;
                var d=doc.RootElement;access=d.TryGetProperty("stage",out var stage)&&stage.GetString()=="access";
                complete=stage.ValueKind==JsonValueKind.String&&stage.GetString()=="complete";
                rows=d.TryGetProperty("rows",out var r)?JsonSerializer.Deserialize<List<MovieCard>>(r.GetRawText())??[]:[];
            }
            else
            {
                using var doc=JsonDocument.Parse(await EpisodeEval(Script("search-state.js"))??"{}");if(Stale())return;
                access=doc.RootElement.TryGetProperty("stage",out var stage)&&stage.GetString()!="results";
                rows=[];
                if(!access)
                {
                    if(episodeSource==Source.Freedisc&&!episodeRouted)
                    {var routed=await EpisodeEval(Script("freedisc-search.js").Replace("__VP_SEARCH_URL__",JsonSerializer.Serialize(Policy.Search(episodeSource,episodeQuery))));if(Stale())return;episodeRouted=routed=="routed";}
                    if(!SearchDocumentMatches(episodeBrowser.CoreWebView2.Source,episodeSource,episodeQuery))return;
                    rows=JsonSerializer.Deserialize<List<MovieCard>>(await EpisodeEval(Script("search-results.js"))??"[]")??[];if(Stale())return;
                    using var outcome=JsonDocument.Parse(await EpisodeEval(Script("search-outcome.js"))??"{}");if(Stale())return;
                    complete=outcome.RootElement.TryGetProperty("empty",out var empty)&&empty.GetBoolean();
                }
            }
            if(Stale())return;
            if(access)
            {
                episodeAccess=true;episodeDeadline=DateTime.UtcNow.AddSeconds(35);
                if(episodeStatus!=EpisodeStatus.Access){episodeStatus=EpisodeStatus.Access;Log("episode.waiting_for_verification");RefreshEpisodeOffer();}
                return;
            }
            episodeAccess=false;if(episodeStatus==EpisodeStatus.Access)episodeStatus=EpisodeStatus.Searching;
            if(episodeVerification)episodeWindow?.Close();
            var valid=rows.Where(m=>Policy.Movie(m.url)&&Policy.Site(m.url)==episodeSource).ToList();
            var matching=valid.Where(m=>nextEpisode!.Matches(m.title)).DistinctBy(m=>MovieCollections.Key(m.url)).Take(6).ToList();
            if(matching.Count>0){FinishEpisode(EpisodeStatus.Found,matching);return;}
            if(complete||valid.Count>0)
            {
                if(!episodeFallback&&episodeCurrent!=null){episodeFallback=true;nextEpisode=episodeCurrent.NextSeason();Log("episode.next_season");await BeginEpisodeSearch();return;}
                FinishEpisode(EpisodeStatus.NoMatch);
            }
        }
        catch(Exception error)
        {
            Log("episode.inspect."+error.GetType().Name);
            if(!Stale()&&DateTime.UtcNow>episodeDeadline&&!episodeAccess)
            {if(!episodeGoogle)GoogleEpisodeSearch();else FinishEpisode(EpisodeStatus.Error);}
        }
        finally{episodeBusy=false;}
    }
    private string EpisodeMessage()=>episodeStatus switch
    {
        EpisodeStatus.Access=>"Wyszukiwanie czeka na wpisanie kodu, zgodę lub logowanie.",
        EpisodeStatus.NoMatch=>"Nie znaleziono kolejnego odcinka ani pierwszego odcinka następnego sezonu.",
        EpisodeStatus.Error=>"Nie udało się zakończyć wyszukiwania. Spróbuj ponownie.",
        EpisodeStatus.Unrecognized=>"Nie rozpoznano numeru odcinka w tytule. Możesz wyszukać kolejny ręcznie.",
        EpisodeStatus.Found=>"Wybierz kolejny odcinek.",
        _=>"Wyszukuję kolejny odcinek…"
    };
    private StackPanel EpisodePanel()
    {
        var panel=new StackPanel{Margin=new Thickness(12)};
        panel.Children.Add(new TextBlock{Text=nextEpisode==null?"Koniec filmu":"Następny odcinek · "+nextEpisode.Query,FontSize=16,Foreground=Brushes.White});
        panel.Children.Add(new TextBlock{Text=EpisodeMessage(),TextWrapping=TextWrapping.Wrap,Margin=new Thickness(0,8,0,8),Foreground=Brushes.White});
        if(episodeCards.Count>0)
        {
            var row=new WrapPanel();foreach(var movie in episodeCards)
            {
                var content=new StackPanel{Width=150};
                if(Policy.Thumbnail(movie.thumbnail))try{content.Children.Add(new Image{Height=84,Stretch=Stretch.UniformToFill,Source=new System.Windows.Media.Imaging.BitmapImage(new Uri(movie.thumbnail))});}catch{}
                content.Children.Add(new TextBlock{Text=movie.title,TextWrapping=TextWrapping.Wrap,MaxHeight=48});
                var button=new Button{Content=content,Margin=new Thickness(4),Padding=new Thickness(5)};button.Click+=(_,_)=>OpenMovie(movie.url,movie);row.Children.Add(button);
            }
            panel.Children.Add(row);
        }
        var actions=new WrapPanel();
        if(episodeAccess){var verify=new Button{Content="Wpisz kod / potwierdź dostęp"};verify.Click+=(_,_)=>OpenEpisodeVerification();actions.Children.Add(verify);}
        if(episodeStatus==EpisodeStatus.Error&&nextEpisode!=null){var retry=new Button{Content="Spróbuj ponownie"};retry.Click+=(_,_)=>{episodeStartTask=BeginEpisodeSearch();};actions.Children.Add(retry);}
        var manual=new Button{Content="Szukaj ręcznie"};manual.Click+=(_,_)=>{Query.Text=nextEpisode?.Query??episodeOriginalTitle;episodeOffer=false;episodeDismissed=true;episodeWindow?.Close();episodeWindow=null;ShowCollection("results");Query.Focus();};actions.Children.Add(manual);
        var dismiss=new Button{Content="Zamknij"};dismiss.Click+=(_,_)=>{episodeOffer=false;episodeDismissed=true;episodeWindow?.Close();episodeWindow=null;};actions.Children.Add(dismiss);panel.Children.Add(actions);return panel;
    }
    private void RefreshEpisodeOffer()
    {
        if(episodeVerification||!episodeOffer||episodeDismissed||closed)return;
        if(episodeWindow?.IsVisible==true)episodeWindow.Content=EpisodePanel();else ShowEpisodeOffer();
    }
    private void ShowEpisodeOffer()
    {
        if(episodeWindow!=null||!episodeOffer||episodeDismissed||closed)return;
        episodeWindow=new Window{Owner=this,Title="Następny odcinek",Content=EpisodePanel(),Width=Math.Min(1040,ActualWidth-40),SizeToContent=SizeToContent.Height,WindowStyle=WindowStyle.ToolWindow,ResizeMode=ResizeMode.NoResize,ShowInTaskbar=false,ShowActivated=false,Background=new SolidColorBrush(Color.FromRgb(20,39,49)),Foreground=Brushes.White,WindowStartupLocation=WindowStartupLocation.CenterOwner};
        var window=episodeWindow;window.Closed+=(_,_)=>{if(episodeWindow==window){episodeWindow=null;episodeDismissed=true;episodeOffer=false;}};
        window.Show();window.Left=Left+(ActualWidth-window.ActualWidth)/2;window.Top=Top+Math.Max(0,ActualHeight-window.ActualHeight-30);
        Log("episode.offer."+episodeStatus);
    }
    private void OpenEpisodeVerification()
    {
        if(episodeBrowser==null)return;
        var old=episodeWindow;episodeWindow=null;old?.Close();episodeVerification=true;
        Workspace.Children.Remove(episodeBrowser);episodeBrowser.Visibility=Visibility.Visible;
        var host=new Window{Owner=this,Title="Potwierdź dostęp · kolejny odcinek",Width=850,Height=650,Content=episodeBrowser,WindowStartupLocation=WindowStartupLocation.CenterOwner};
        host.Closed+=(_,_)=>{episodeVerification=false;host.Content=null;episodeBrowser.Visibility=Visibility.Hidden;Workspace.Children.Insert(0,episodeBrowser);if(episodeWindow==host)episodeWindow=null;if(episodeOffer&&!closed)ShowEpisodeOffer();};
        episodeWindow=host;host.Show();
    }
}
