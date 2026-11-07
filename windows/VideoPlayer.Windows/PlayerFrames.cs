using System.Text.Json;
using Microsoft.Web.WebView2.Core;
namespace VideoPlayer.Windows;
public partial class MainWindow
{
    private sealed class PlayerFrame(CoreWebView2Frame frame,Func<string> parent,int depth)
    {
        public CoreWebView2Frame Frame=frame;public Func<string> Parent=parent;public int Depth=depth;public string Page="about:blank";
    }
    private readonly Dictionary<CoreWebView2Frame,PlayerFrame> trackedFrames=[];
    private readonly Dictionary<string,string> parentPages=[];
    private readonly Dictionary<string,int> playerDepths=[];
    private void RegisterPlayerFrame(CoreWebView2Frame frame,Func<string> parent,int depth)
    {
        if(depth>12)return;
        if(trackedFrames.TryGetValue(frame,out var old)){if(depth>old.Depth){old.Depth=depth;old.Parent=parent;}return;}
        var info=new PlayerFrame(frame,parent,depth);trackedFrames[frame]=info;Log("frame.created.depth."+depth);
        try{frame.FrameCreated+=(_,e)=>RegisterPlayerFrame(e.Frame,()=>info.Page,info.Depth+1);}
        catch(Exception error){Log("frame.child_tracking_unsupported."+error.GetType().Name);}
        frame.NavigationStarting+=(_,e)=>info.Page=e.Uri;
        frame.DOMContentLoaded+=(_,_)=>SendFrameLayout(info,filmView);
        frame.NavigationCompleted+=(_,e)=>{Log("frame.navigation.depth."+depth+"."+(e.IsSuccess?"complete":e.WebErrorStatus.ToString()));if(e.IsSuccess)SendFrameLayout(info,filmView);};
        frame.WebMessageReceived+=(_,e)=>
        {
            info.Page=e.Source;playbackFrames[e.Source]=frame;parentPages[e.Source]=info.Parent();playerDepths[e.Source]=info.Depth;
            SafeMessage(e,frame.PostWebMessageAsJson);
        };
        frame.Destroyed+=(_,_)=>
        {
            trackedFrames.Remove(frame);
            foreach(var key in playbackFrames.Where(x=>ReferenceEquals(x.Value,frame)).Select(x=>x.Key).ToArray())
            {playbackFrames.Remove(key);parentPages.Remove(key);playerDepths.Remove(key);endpoints.Remove(key);}
        };
    }
    private string PlayerParentOrigin(string page)
    {
        var parent=parentPages.TryGetValue(page,out var value)?value:Browser.CoreWebView2.Source;
        if(!MediaOrigin(parent))parent=Browser.CoreWebView2.Source;
        return new Uri(parent).GetLeftPart(UriPartial.Authority);
    }
    private int PlayerPriority(string page)=>(new Uri(page).AbsolutePath.StartsWith("/embed",StringComparison.Ordinal)?100:10)+(playerDepths.TryGetValue(page,out var depth)?depth:0);
    private void SendFrameLayout(PlayerFrame info,bool enabled)
    {
        if(closed||!MediaOrigin(info.Page)||mode!=Mode.Movie)return;
        try
        {
            var parent=MediaOrigin(info.Parent())?new Uri(info.Parent()).GetLeftPart(UriPartial.Authority):new Uri(Browser.CoreWebView2.Source).GetLeftPart(UriPartial.Authority);
            info.Frame.PostWebMessageAsJson(JsonSerializer.Serialize(new{type="desktop-layout",desktopFit=enabled,parentOrigin=parent}));
        }
        catch(Exception error){Log("frame.layout."+error.GetType().Name);}
    }
}
