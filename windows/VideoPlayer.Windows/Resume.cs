using System.ComponentModel;
using System.Text.Json;
using System.Windows;
using Microsoft.Web.WebView2.Core;
namespace VideoPlayer.Windows;
public partial class MainWindow
{
    private readonly Dictionary<string,TaskCompletionSource<bool>> flushRequests=[];
    private readonly Dictionary<string,CoreWebView2Frame> playbackFrames=[];
    private string progressPage="";
    private int progressPriority=-1,progressEpoch=-1;
    private double loggedPosition=-1000;
    private bool closing;
    private int openSequence;
    private void ResetTimeline(){progressPage="";progressPriority=-1;progressEpoch=epoch;loggedPosition=-1000;playbackFrames.Clear();}
    private bool AcceptProgress(string page,double position,double duration)
    {
        if(!double.IsFinite(position)||!double.IsFinite(duration)||duration<=0||duration>86400||position<0||position>duration+2)return false;
        if(progressEpoch!=epoch){progressPage="";progressPriority=-1;progressEpoch=epoch;}
        int priority=PlayerPriority(page);
        if(page!=progressPage&&priority<progressPriority)return false;
        if(page!=progressPage&&priority==progressPriority&&progressPage.Length>0)return false;
        progressPage=page;progressPriority=priority;
        if(Math.Abs(position-loggedPosition)>=30){loggedPosition=position;Log("progress.saved."+(int)position+".duration."+(int)duration);}
        return true;
    }
    private async Task FlushPlayback()
    {
        if(!ready||closed||mode!=Mode.Movie||currentMovie==null||endpoints.Count==0)return;
        var pending=new List<Task>();var ids=new List<string>();
        try
        {
            foreach(var endpoint in endpoints.Values.Where(e=>e.Epoch==epoch).ToArray())
            {
                var id=Guid.NewGuid().ToString("N");var completion=new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
                flushRequests[id]=completion;ids.Add(id);pending.Add(completion.Task);
                try{endpoint.Reply(JsonSerializer.Serialize(new{type="flush",token=endpoint.Token,requestId=id}));}catch{completion.TrySetResult(false);}
            }
            if(pending.Count>0)await Task.WhenAll(pending).WaitAsync(TimeSpan.FromMilliseconds(1200));
        }
        catch(Exception error){Log("progress.flush."+error.GetType().Name);}
        finally{foreach(var id in ids)flushRequests.Remove(id);}
    }
    private async void WindowClosing(object? sender,CancelEventArgs e)
    {
        if(closed)return;
        if(!smoke)
        {
            e.Cancel=true;if(closing)return;closing=true;
            await FlushPlayback();
        }
        closed=true;++epoch;timer.Stop();episodeOffer=false;episodeTimer?.Stop();episodeWindow?.Close();episodeBrowser?.Dispose();Browser.Dispose();
        if(!smoke)Close();
    }
}
