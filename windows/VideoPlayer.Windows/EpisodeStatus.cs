using System.Windows;
namespace VideoPlayer.Windows;
public partial class MainWindow
{
    private double episodePosition,episodeDuration;
    private DateTime episodeClockAt;
    private void UpdateEpisodeClock(double position,double duration,bool ended)
    {
        episodePosition=position;episodeDuration=duration;episodeClockAt=DateTime.UtcNow;
        ConsiderEpisode(position,duration,ended);
    }
    private void OpenEpisodePanel()
    {
        if(mode!=Mode.Movie||currentMovie==null)return;
        Log("episode.manual_open");episodeDismissed=false;episodeOffer=true;
        if(episodeStatus==EpisodeStatus.NotStarted)ConsiderEpisode(episodePosition,episodeDuration,true);
        RefreshEpisodeOffer();ShowEpisodeOffer();
    }
    private void NextEpisodeClick(object sender,RoutedEventArgs e)=>OpenEpisodePanel();
    private string EpisodeClockText()=>episodeClockAt==default?"Nie odebrano jeszcze czasu filmu. Panel można otworzyć ręcznie klawiszem N.":"Odczyt filmu: "+TimeSpan.FromSeconds(Math.Max(0,episodePosition)).ToString(@"hh\:mm\:ss")+" / "+TimeSpan.FromSeconds(Math.Max(0,episodeDuration)).ToString(@"hh\:mm\:ss");
    private IEnumerable<string> RuntimeSnapshot()
    {
        yield return "runtime.webview="+Browser.CoreWebView2.Environment.BrowserVersionString;
        yield return "runtime.mode="+mode;
        yield return "runtime.player_endpoints="+endpoints.Count;
        yield return "runtime.frame_depths="+string.Join(',',playerDepths.Values.OrderBy(x=>x));
        yield return "runtime.clock_position="+(int)episodePosition+" duration="+(int)episodeDuration;
        yield return "runtime.clock_age="+(episodeClockAt==default?-1:(int)(DateTime.UtcNow-episodeClockAt).TotalSeconds);
        yield return "runtime.episode_state="+episodeStatus+" offer="+episodeOffer+" dismissed="+episodeDismissed;
        yield return "runtime.episode_marker="+(EpisodePlan.Resolve(currentMovie?.title,episodeOriginalTitle,currentMovie?.url)!=null);
        yield return "runtime.episode_cards="+episodeCards.Count+" access="+episodeAccess;
    }
}
