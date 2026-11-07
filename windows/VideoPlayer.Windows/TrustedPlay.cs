using System.Text.Json;
using Microsoft.Web.WebView2.Core;
namespace VideoPlayer.Windows;
public partial class MainWindow
{
    private readonly Dictionary<string,DateTime> playRequests=[];
    private bool verificationActive;
    private string PlayRequest()
    {
        foreach(var id in playRequests.Where(x=>DateTime.UtcNow-x.Value>TimeSpan.FromSeconds(3)).Select(x=>x.Key).ToArray())playRequests.Remove(id);
        var request=Guid.NewGuid().ToString("N");playRequests[request]=DateTime.UtcNow;return request;
    }
    private async Task TrustedPlayTap(string page,JsonElement data)
    {
        if(mode!=Mode.Movie||verificationActive||closed||page!=Browser.CoreWebView2.Source||!Policy.Movie(page))return;
        if(!data.TryGetProperty("id",out var idValue)||idValue.GetString() is not string id||!playRequests.Remove(id,out var issued)||DateTime.UtcNow-issued>TimeSpan.FromSeconds(3))return;
        if(!data.TryGetProperty("x",out var xv)||!data.TryGetProperty("y",out var yv)||!xv.TryGetDouble(out var nx)||!yv.TryGetDouble(out var ny)||!double.IsFinite(nx)||!double.IsFinite(ny)||nx<=0||nx>=1||ny<=0||ny>=1)return;
        var current=epoch;
        try
        {
            using var metrics=JsonDocument.Parse(await Browser.CoreWebView2.CallDevToolsProtocolMethodAsync("Page.getLayoutMetrics","{}").WaitAsync(TimeSpan.FromSeconds(3)));
            var viewport=metrics.RootElement.GetProperty("cssVisualViewport");
            double x=nx*viewport.GetProperty("clientWidth").GetDouble(),y=ny*viewport.GetProperty("clientHeight").GetDouble();
            if(current!=epoch||closed||verificationActive||mode!=Mode.Movie)return;
            await Browser.CoreWebView2.CallDevToolsProtocolMethodAsync("Input.dispatchMouseEvent",JsonSerializer.Serialize(new{type="mousePressed",x,y,button="left",buttons=1,clickCount=1})).WaitAsync(TimeSpan.FromSeconds(3));
            await Browser.CoreWebView2.CallDevToolsProtocolMethodAsync("Input.dispatchMouseEvent",JsonSerializer.Serialize(new{type="mouseReleased",x,y,button="left",buttons=0,clickCount=1})).WaitAsync(TimeSpan.FromSeconds(3));
            Log("media.original_play_click");
        }
        catch(Exception error){Log("media.original_play_click."+error.GetType().Name);}
    }
    private void AdDiagnostic(JsonElement data)
    {
        string Field(string name,string[] allowed)=>data.TryGetProperty(name,out var v)&&v.ValueKind==JsonValueKind.String&&allowed.Contains(v.GetString())?v.GetString()!:"unknown";
        bool Flag(string name)=>data.TryGetProperty(name,out var v)&&v.ValueKind is JsonValueKind.True;
        var kind=Field("event",["ad-player","ad-layer"]);
        if(kind=="ad-player")Log("player."+Field("signal",["snapshot","adStarted","adComplete","adSkipped","adError"])+".state."+Field("state",["idle","buffering","paused","playing","complete","error"])+".source."+Flag("sourceAssigned")+".paused."+Flag("paused"));
        else if(kind=="ad-layer")Log("layer."+Field("kind",["freedisc-panel-1","freedisc-panel-2","adblock-message","background-wait","jw-vast"])+".visible."+Flag("visible")+".covers."+Flag("coversVideo"));
    }
}
