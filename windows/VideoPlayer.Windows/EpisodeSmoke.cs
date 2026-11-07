using System.IO;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Web.WebView2.Core;
namespace VideoPlayer.Windows;
public partial class MainWindow
{
    private string episodeFixture="found";
    private void ConfigureEpisodeFixtures(CoreWebView2 core)
    {
        core.AddWebResourceRequestedFilter("*",CoreWebView2WebResourceContext.Document);
        core.WebResourceRequested+=(_,e)=>
        {
            string html=episodeFixture switch
            {
                "captcha"=>"<!doctype html><form><p>Wpisz kod</p><input name='captcha_code'></form>",
                "empty"=>"<!doctype html><div class='search-results'>Brak wyników</div>",
                _=>"<!doctype html><div class='search-results'><a href='https://vider.info/vid/+fepisodenext' title='Smoke Serial S01E02 HD'>Smoke Serial S01E02 HD</a></div>"
            };
            e.Response=core.Environment.CreateWebResourceResponse(new MemoryStream(Encoding.UTF8.GetBytes(html)),200,"OK","Content-Type: text/html; charset=utf-8\r\nCache-Control: no-store");
        };
    }
    private async Task EpisodeUntil(Func<bool> condition,string message)
    {
        var deadline=DateTime.UtcNow.AddSeconds(12);
        while(!condition()){if(DateTime.UtcNow>deadline)throw new Exception(message+"; status="+episodeStatus+"; "+string.Join(" | ",log.Where(l=>l.Contains("episode."))));await Task.Delay(100);}
    }
    private void EpisodeCase(string fixture,string title="Smoke_Serial_S01.E01")
    {
        episodeTimer?.Stop();episodeOffer=false;var window=episodeWindow;episodeWindow=null;window?.Close();
        currentMovie=new(title,"https://vider.info/vid/+fepisodecurrent","");CollectionAction(()=>collections?.Opened(currentMovie));
        ResetEpisodeState();episodeFixture=fixture;
    }
    private async Task SmokeEpisodes(CoreWebView2Frame embeddedFrame)
    {
        EpisodeCase("found");
        ConsiderEpisode(59,100);if(episodeStatus!=EpisodeStatus.NotStarted)throw new Exception("Episode search started before 40 seconds");
        ConsiderEpisode(60,100);if(episodeStartTask!=null)await episodeStartTask;
        await EpisodeUntil(()=>episodeStatus==EpisodeStatus.Found,"Next episode search failed");
        if(episodeWindow!=null||episodeCards.Count!=1)throw new Exception("Episode tiles appeared too early or are missing");
        ConsiderEpisode(89,100);if(episodeWindow!=null)throw new Exception("Episode UI appeared before 10 seconds");
        ConsiderEpisode(90,100);if(episodeWindow?.IsVisible!=true)throw new Exception("Episode tiles not visible above movie");
        episodeWindow.Close();ConsiderEpisode(95,100);if(episodeWindow!=null)throw new Exception("Dismissed episode offer reopened");
        EpisodeCase("empty");ConsiderEpisode(100,100,true);if(episodeStartTask!=null)await episodeStartTask;
        await EpisodeUntil(()=>episodeStatus==EpisodeStatus.NoMatch,"Explicit no-episode result missing");
        if(!episodeFallback||nextEpisode?.Query!="Smoke Serial S02E01"||episodeWindow?.IsVisible!=true||!EpisodeMessage().Contains("Nie znaleziono"))throw new Exception("Season fallback or visible no-match message failed");
        EpisodeCase("captcha");ConsiderEpisode(100,100,true);if(episodeStartTask!=null)await episodeStartTask;
        await EpisodeUntil(()=>episodeStatus==EpisodeStatus.Access,"Episode verification prompt missing");
        if(episodeWindow?.IsVisible!=true)throw new Exception("Verification required but no visible offer");
        OpenEpisodeVerification();if(!episodeVerification||episodeBrowser?.Visibility!=Visibility.Visible)throw new Exception("Episode verification browser not visible");
        await episodeBrowser.ExecuteScriptAsync("document.body.innerHTML=\"<div class='search-results'><a href='https://vider.info/vid/+fepisodenext' title='Smoke Serial S01E02 HD'>Smoke Serial S01E02 HD</a></div>\";");
        await EpisodeUntil(()=>episodeStatus==EpisodeStatus.Found,"Episode search did not resume after verification");
        if(episodeVerification||episodeWindow?.IsVisible!=true)throw new Exception("Verification did not return to native episode tiles");
        EpisodeCase("found","Film bez numeru odcinka");ConsiderEpisode(100,100,true);
        if(episodeStatus!=EpisodeStatus.Unrecognized||episodeWindow?.IsVisible!=true||!EpisodeMessage().Contains("Nie rozpoznano"))throw new Exception("Unrecognized title silently skipped episode UI");
        EpisodeCase("found");episodeCurrent=EpisodePlan.Parse(currentMovie!.title);nextEpisode=episodeCurrent!.Next();episodeEpoch=epoch;episodeOffer=true;episodeStatus=EpisodeStatus.Searching;episodeGoogle=true;episodeDeadline=DateTime.UtcNow.AddSeconds(-1);
        await InspectEpisode();if(episodeStatus!=EpisodeStatus.Error||episodeWindow?.IsVisible!=true||EpisodeMessage().Contains("Nie znaleziono"))throw new Exception("Search timeout must show error, not no-match or silence");
        EpisodeCase("empty");
        await embeddedFrame.ExecuteScriptAsync("window.smokePlayer.getDuration=()=>100;window.smokePlayer.events.complete();");
        await EpisodeUntil(()=>episodeStatus==EpisodeStatus.NoMatch,"Player complete event did not display an episode outcome");
        if(episodeWindow?.IsVisible!=true)throw new Exception("Player complete event produced no visible panel");
        episodeOffer=false;episodeTimer?.Stop();var panel=episodeWindow;episodeWindow=null;panel?.Close();ResetEpisodeState();
    }
}
