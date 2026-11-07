using System.Text.Json;
using System.Windows;
using Microsoft.Web.WebView2.Core;
namespace VideoPlayer.Windows;
public partial class MainWindow
{
    private sealed record Endpoint(string Token,int Epoch,Action<string> Reply);
    private readonly Dictionary<string,Endpoint> endpoints=[];
    private bool autoStart=true,filmView;
    private Window? episodeWindow;
    private void ResetPlayback(){endpoints.Clear();playRequests.Clear();verificationActive=false;filmView=false;autoStart=true;episodeOffer=false;nextEpisode=null;episodeTimer?.Stop();episodeBrowser?.CoreWebView2?.Stop();episodeWindow?.Close();episodeWindow=null;nextEpisode=null;}
    private static bool MediaOrigin(string value)=>Policy.Allowed(value)||Uri.TryCreate(value,UriKind.Absolute,out var u)&&u.Scheme=="https"&&u.Port==443&&u.UserInfo.Length==0&&new[]{"stream.freedisc.pl","stream2.freedisc.pl","ovh.freedisc.pl"}.Contains(u.IdnHost);
    private async Task ConfigurePlayback(CoreWebView2 core)
    {
        core.WebMessageReceived+=(_,e)=>SafeMessage(e,core.PostWebMessageAsJson);
        core.FrameCreated+=(_,args)=>
        {
            var frame=args.Frame;
            Log("frame.created");
            frame.NavigationCompleted+=(_,e)=>Log("frame.navigation."+(e.IsSuccess?"complete":e.WebErrorStatus.ToString()));
            frame.WebMessageReceived+=(_,e)=>SafeMessage(e,frame.PostWebMessageAsJson);
        };
        var wrapper="""
        if(/^https:\/\/(www\.)?(vider\.(info|pl)|freedisc\.pl)$/.test(location.origin)||['https://stream.freedisc.pl','https://stream2.freedisc.pl','https://ovh.freedisc.pl'].includes(location.origin)){
        window.vpTvTouch={postMessage:function(s){window.chrome.webview.postMessage(JSON.stringify({event:'play-tap',page:location.href,data:JSON.parse(s)}))}};
        window.vpDiagnostics={postMessage:function(s){window.chrome.webview.postMessage(JSON.stringify({event:'diagnostic',page:location.href,data:JSON.parse(s)}))}};
        window.vpPlayback={postMessage:function(s){window.chrome.webview.postMessage(s)}};
        window.chrome.webview.addEventListener('message',function(e){const d=e.data;if(d&&typeof d.desktopFit==='boolean'&&window.__vpSetDesktopFit)window.__vpSetDesktopFit(d.desktopFit,d.parentOrigin);if(d&&d.type==='desktop-layout')return;if(window.vpPlayback.onmessage)window.vpPlayback.onmessage({data:JSON.stringify(d)})});
        document.addEventListener('keydown',function(e){if(e.defaultPrevented||e.ctrlKey||e.altKey||e.metaKey||e.repeat)return;const t=e.target;if(t&&(t.isContentEditable||/^(INPUT|TEXTAREA|SELECT|BUTTON|A)$/.test(t.tagName)))return;if(!document.querySelector('video,.jwplayer,[data-vp-player]'))return;const a=e.code==='Space'||e.code==='Enter'?'toggle':e.code==='ArrowLeft'?'back':e.code==='ArrowRight'?'forward':null;if(a){e.preventDefault();window.chrome.webview.postMessage(JSON.stringify({event:'key',page:location.href,action:a}))}},true);
        """;
        await core.AddScriptToExecuteOnDocumentCreatedAsync(wrapper+Script("desktop-fit.js")+"\n"+Script("frame-fit.js")+"\n"+Script("tv-play-tap.js")+"\n"+Script("tv-media.js")+"\n"+Script("original-autostart.js")+"\n"+Script("playback-progress.js")+"\n"+Script("ad-diagnostics.js")+"\n}");
    }
    private void SafeMessage(CoreWebView2WebMessageReceivedEventArgs e,Action<string> reply)
    {try{PlaybackMessage(e.Source,e.TryGetWebMessageAsString(),reply);}catch(Exception error){Log("bridge."+error.GetType().Name);}}
    private void PlaybackMessage(string origin,string json,Action<string> reply)
    {
        try
        {
            if(closed||mode!=Mode.Movie||currentMovie==null||!MediaOrigin(origin)||!Policy.Movie(Browser.CoreWebView2.Source))return;
            using var doc=JsonDocument.Parse(json);var d=doc.RootElement;
            if(!d.TryGetProperty("page",out var page)||page.GetString()!=origin)return;
            var kind=d.GetProperty("event").GetString();
            if(kind=="play-tap"){_=TrustedPlayTap(origin,d.GetProperty("data").Clone());return;}
            if(kind=="diagnostic"){AdDiagnostic(d.GetProperty("data"));return;}
            if(kind=="key"){var action=d.GetProperty("action").GetString();if(action is "toggle" or "back" or "forward")MediaCommand(action);return;}
            if(kind=="layout"){
                int Number(string key)=>d.TryGetProperty(key,out var v)&&v.TryGetInt32(out var n)&&n>=0&&n<=20000?n:0;
                Log($"layout.{Number("width")}x{Number("height")}.viewport.{Number("viewportWidth")}x{Number("viewportHeight")}",origin);return;
            }
            if(kind=="hello")
            {
                Log("playback.hello",origin);
                var token=Guid.NewGuid().ToString("N");endpoints[origin]=new(token,epoch,reply);
                var old=collections?.Find(currentMovie.url);
                var position=old!=null&&old.Duration-old.Position>10?old.Position:0;
                reply(JsonSerializer.Serialize(new{token,position,duration=old?.Duration??0,autostart=autoStart&&!smoke,desktopFit=filmView,parentOrigin=new Uri(Browser.CoreWebView2.Source).GetLeftPart(UriPartial.Authority)}));return;
            }
            if(!endpoints.TryGetValue(origin,out var endpoint)||endpoint.Epoch!=epoch||d.GetProperty("token").GetString()!=endpoint.Token)return;
            if(kind is "autoplay-started" or "autoplay-expired" or "autoplay-cancelled"){autoStart=false;Log(kind);return;}
            if(kind=="autoplay-request"&&autoStart&&!smoke&&!verificationActive){Log("autoplay.request");SendMedia(endpoint,"play",false);return;}
            if(kind=="media-command-result"){Log("media.command.handled."+(d.TryGetProperty("handled",out var h)&&h.ValueKind==JsonValueKind.True));return;}
            if(kind=="progress")
            {
                var position=d.GetProperty("position").GetDouble();var duration=d.GetProperty("duration").GetDouble();
                CollectionAction(()=>collections?.Progress(currentMovie.url,position,duration));
                ConsiderEpisode(position,duration);
            }
        }
        catch(Exception error){Log("playback."+error.GetType().Name);}
    }
    private async Task SetFilmLayout(bool enabled)
    {
        filmView=enabled;
        foreach(var entry in endpoints.Where(e=>e.Value.Epoch==epoch).ToArray())
        {
            try{entry.Value.Reply(JsonSerializer.Serialize(new{type="desktop-layout",desktopFit=enabled,parentOrigin=new Uri(Browser.CoreWebView2.Source).GetLeftPart(UriPartial.Authority)}));}
            catch(Exception error){Log("layout.reply."+error.GetType().Name);}
        }
        await Browser.ExecuteScriptAsync("if(window.__vpSetDesktopFit)window.__vpSetDesktopFit("+(enabled?"true":"false")+");");
    }
    private void SendMedia(Endpoint endpoint,string action,bool manual)=>endpoint.Reply(JsonSerializer.Serialize(new{type="media-command",token=endpoint.Token,action,manual,requestId=PlayRequest(),parentOrigin=new Uri(Browser.CoreWebView2.Source).GetLeftPart(UriPartial.Authority)}));
    private async void MediaCommand(string action)
    {
        if(mode!=Mode.Movie||!ready)return;
        autoStart=false;playRequests.Clear();
        try
        {
            var endpoint=endpoints.Where(x=>x.Value.Epoch==epoch).OrderByDescending(x=>new Uri(x.Key).AbsolutePath.StartsWith("/embed")).Select(x=>x.Value).FirstOrDefault();
            if(endpoint!=null)SendMedia(endpoint,action,true);
            else await Browser.ExecuteScriptAsync("if(window.__vpCancelAutoStart)window.__vpCancelAutoStart();if(window.__vpTvCommand)window.__vpTvCommand("+JsonSerializer.Serialize(action)+","+JsonSerializer.Serialize(PlayRequest())+");");
        }
        catch(Exception error){Log("media."+error.GetType().Name);}
    }
    private void PlayClick(object sender,RoutedEventArgs e)=>MediaCommand("toggle");
    private void GoogleClick(object sender,RoutedEventArgs e)
    {
        if(!ready||Query.Text.Trim().Length is <1 or >120)return;
        activeQuery=Query.Text.Trim();StartGoogle();
    }
    private void StartGoogle()
    {
        LeaveFullscreen();++epoch;mode=Mode.Search;googleSearch=true;searching=true;searchTicks=0;searchDeadline=DateTime.UtcNow.AddSeconds(35);
        collection="results";searchCards=[];Cards.Children.Clear();Welcome.Visibility=Visibility.Collapsed;PlayerTools.Visibility=Visibility.Collapsed;
        Browser.CoreWebView2.Stop();Browser.CoreWebView2.Navigate(Policy.GoogleSearch(source,activeQuery));
        ShowBrowser();Status.Text="Awaryjne wyszukiwanie Google · w razie potrzeby potwierdź zgodę na stronie. Wyniki pojawią się jako kafelki.";
    }
    private static bool SearchDocumentMatches(string url,Source selected,string query)
    {
        if(!Policy.Allowed(url)||Policy.Site(url)!=selected)return false;
        return Uri.UnescapeDataString(new Uri(url).AbsolutePath).TrimEnd('/')==Uri.UnescapeDataString(new Uri(Policy.Search(selected,query)).AbsolutePath).TrimEnd('/');
    }
    private async Task<bool> SearchEmpty(int current)
    {
        using var doc=JsonDocument.Parse(await Eval(Script("search-outcome.js"))??"{}");
        return current==epoch&&doc.RootElement.TryGetProperty("empty",out var empty)&&empty.GetBoolean();
    }
    private async Task ReadGoogle(int current)
    {
        if(!searching)return;
        var raw=await Eval(Script("google-results.js").Replace("__VP_SOURCE__",JsonSerializer.Serialize(source==Source.Freedisc?"FREEDISC":"VIDER")));
        if(current!=epoch||closed)return;
        using var doc=JsonDocument.Parse(raw??"{}");var d=doc.RootElement;
        var stage=d.TryGetProperty("stage",out var s)?s.GetString():"pending";
        if(stage=="access"){searchDeadline=DateTime.UtcNow.AddSeconds(35);ShowBrowser();Status.Text="Google wymaga potwierdzenia. Przewiń stronę myszą i dokończ ten krok tutaj; wyszukiwanie zostanie wznowione.";return;}
        var rows=d.TryGetProperty("rows",out var r)?JsonSerializer.Deserialize<List<MovieCard>>(r.GetRawText())??[]:[];
        var valid=rows.Where(m=>Policy.Movie(m.url)&&Policy.Site(m.url)==source).Select(m=>m with {url=Policy.MovieUrl(m.url)}).DistinctBy(m=>MovieCollections.Key(m.url)).Take(20).ToList();
        if(valid.Count>0||stage=="complete")
        {searching=false;searchCards=valid;collection="results";Render(valid);Status.Text=valid.Count>0?$"Znaleziono: {valid.Count}. Kliknij miniaturkę.":"Brak wyników dla podanego tytułu.";}
    }
}
