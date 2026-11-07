using Microsoft.Web.WebView2.Core;
namespace VideoPlayer.Windows;
public partial class MainWindow
{
    private async Task SmokeResume(string root,string embed,CoreWebView2Frame frame)
    {
        // Interrupt between time events: the history action must request the last real position.
        await frame.ExecuteScriptAsync("window.smokePlayer.position=487;");
        ShowCollection("history");
        await EpisodeUntil(()=>mode==Mode.Results,"History transition did not finish saving progress");
        if(collections?.Find(root)?.Position!=487)throw new Exception("Latest position was not flushed before history");
        var saved=collections.Find(root)!.Card;
        OpenMovie(saved.url,saved);
        await EpisodeUntil(()=>endpoints.ContainsKey(embed)&&playbackFrames.ContainsKey(embed),"Reopened history movie did not register its player");
        frame=playbackFrames[embed];
        if(collections.Find(root)?.Position!=487)throw new Exception("Startup zero overwrote the bookmark");
        await FocusMovie(true,epoch);await Task.Delay(300);
        MediaCommand("play");
        await EpisodeUntil(()=>collections.Find(root)?.Position==487&&log.Any(l=>l.Contains("resume-applied")),"History movie did not seek to saved position");
        if(await frame.ExecuteScriptAsync("Math.round(window.smokePlayer.position)")!="487")throw new Exception("Restored bookmark without actually seeking player");
        await frame.ExecuteScriptAsync("window.smokePlayer.position=555;window.smokePlayer.state='paused';window.smokePlayer.events.pause();");
        await EpisodeUntil(()=>collections.Find(root)?.Position==555,"Pause did not save current position immediately");
        // Production restart persistence is covered by policy checks; this instance uses a temporary smoke profile.
        if(collections.Find(root)?.Completed==true)throw new Exception("Interrupted movie incorrectly marked complete");
    }
}
