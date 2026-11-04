package pl.example.viderplayer;

import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import static org.junit.Assert.*;

/** Presentation state and native UI, with all test content supplied as local HTML. */
@RunWith(AndroidJUnit4.class)
public final class AutomaticPresentationTest {
    private static final String URL="https://vider.info/vid/+fautotest";
    private static final String PLAYER="<div class='video-frame'><video controls style='width:100%;height:100%'></video></div>";
    private static Object field(Object target,String name){
        try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}
    }
    private static WebView web(MainActivity activity){return (WebView)field(activity,"web");}
    private static ActivityScenario<MainActivity> launch(){
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(URL));
        return ActivityScenario.launch(intent);
    }
    private static void load(ActivityScenario<MainActivity> scenario,String body){loadAt(scenario,body,URL);}
    private static void loadAt(ActivityScenario<MainActivity> scenario,String body,String destination){
        final String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body data-vp-test='presentation'>"+body+"</body></html>";
        scenario.onActivity(activity->{
            WebView web=web(activity);web.stopLoading();
            WebViewClient original=web.getWebViewClient();
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream(request.isForMainFrame()?html.getBytes(java.nio.charset.StandardCharsets.UTF_8):new byte[0]));}
                @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap bitmap){original.onPageStarted(view,url,bitmap);}
                @Override public void onPageFinished(WebView view,String url){original.onPageFinished(view,url);}
            });
            web.loadUrl(destination+"?fixture="+java.util.UUID.randomUUID());
        });
        long loaded=SystemClock.elapsedRealtime()+15000;
        while(!"true".equals(eval(scenario,"!!document.querySelector('body[data-vp-test=presentation]')"))){
            if(SystemClock.elapsedRealtime()>loaded)fail("Local presentation document was not loaded");
            SystemClock.sleep(100);
        }
    }
    private static void await(ActivityScenario<MainActivity> scenario,Predicate<MainActivity> condition,String message){
        AtomicBoolean ready=new AtomicBoolean();long end=SystemClock.elapsedRealtime()+15000;
        do{scenario.onActivity(activity->ready.set(condition.test(activity)));if(ready.get())return;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<end);
        AtomicReference<String> details=new AtomicReference<>("");
        scenario.onActivity(activity->details.set(((DiagnosticLog)field(activity,"diagnostics")).export()+" resumed="+field(activity,"resumed")+" pending="+((PresentationPolicy)field(activity,"presentation")).shouldEnter()+" url="+web(activity).getUrl()));
        fail(message+" · "+details.get()+" · DOM="+eval(scenario,"JSON.stringify({fixture:!!document.querySelector('[data-vp-test]'),focus:!!document.getElementById('vp-focus-style'),videos:document.querySelectorAll('video').length})"));
    }
    private static String eval(ActivityScenario<MainActivity> scenario,String script){
        AtomicReference<String> value=new AtomicReference<>();scenario.onActivity(activity->web(activity).evaluateJavascript(script,value::set));
        long end=SystemClock.elapsedRealtime()+5000;while(value.get()==null&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);
        assertNotNull(value.get());return value.get();
    }
    @Test public void movieOpensFocusedAndFullscreenWithoutAutoplayAndBackStaysInControls(){
        try(ActivityScenario<MainActivity> scenario=launch()){
            load(scenario,PLAYER+"<p id='comments'>Comments</p>");
            await(scenario,activity->(Boolean)field(activity,"playerFullscreen"),"Movie did not enter automatic fullscreen");
            assertEquals("true",eval(scenario,"!!document.getElementById('vp-focus-style')"));
            assertEquals("true",eval(scenario,"document.querySelector('video').paused"));
            scenario.onActivity(activity->{
                android.widget.LinearLayout content=(android.widget.LinearLayout)field(activity,"content");
                for(int i=0;i<content.getChildCount();i++)if(content.getChildAt(i)!=web(activity))assertEquals(View.GONE,content.getChildAt(i).getVisibility());
                assertEquals(TvUi.enabled(activity)?View.GONE:View.VISIBLE,((View)field(activity,"fullscreenControls")).getVisibility());
                activity.onBackPressed();
                assertFalse((Boolean)field(activity,"playerFullscreen"));
                assertFalse(((PresentationPolicy)field(activity,"presentation")).shouldEnter());
            });
            SystemClock.sleep(2200);
            scenario.onActivity(activity->assertFalse("Back must not trigger fullscreen again",(Boolean)field(activity,"playerFullscreen")));
            assertEquals("true",eval(scenario,"!!document.getElementById('vp-focus-style')"));
        }
    }
    @Test public void waitsForADynamicallyInsertedPlayer(){
        try(ActivityScenario<MainActivity> scenario=launch()){
            load(scenario,"<p>Loading</p><script>setTimeout(function(){var p=document.createElement('div');p.className='video-frame';p.innerHTML='<video controls></video>';document.body.appendChild(p);},1800)</script>");
            await(scenario,activity->(Boolean)field(activity,"playerFullscreen"),"Late player did not enter automatic fullscreen");
        }
    }
    @Test public void aLaterCodeFormExitsFullscreenAndCompletingItResumesPresentation(){
        try(ActivityScenario<MainActivity> scenario=launch()){
            load(scenario,PLAYER+"<div id='access' style='display:none'><form><input name='captcha_code' id='code'><button type='button'>Wchodzę</button></form></div>");
            await(scenario,activity->(Boolean)field(activity,"playerFullscreen"),"Initial fullscreen did not start");
            eval(scenario,"document.getElementById('access').style.display='block'");
            await(scenario,activity->!(Boolean)field(activity,"playerFullscreen")&&((View)field(activity,"codeRow")).getVisibility()==View.VISIBLE,"Later challenge was hidden by fullscreen");
            assertEquals("false",eval(scenario,"!!document.getElementById('vp-focus-style')"));
            eval(scenario,"document.getElementById('access').remove()");
            await(scenario,activity->(Boolean)field(activity,"playerFullscreen")&&((View)field(activity,"codeRow")).getVisibility()==View.GONE,"Presentation did not resume after the local challenge was removed");
        }
    }

    @Test public void sameServiceEmbedOpensFullscreen(){
        try(ActivityScenario<MainActivity> scenario=launch()){
            loadAt(scenario,PLAYER,"https://vider.pl/embed/video/test");
            await(scenario,a->(Boolean)field(a,"playerFullscreen"),"Embed navigation did not enter fullscreen");
        }
    }
    @Test public void replacementPlayerKeepsFilmFocus(){
        try(ActivityScenario<MainActivity> scenario=launch()){
            load(scenario,PLAYER);
            await(scenario,a->(Boolean)field(a,"playerFullscreen"),"Initial presentation missing");
            eval(scenario,"document.body.innerHTML="+org.json.JSONObject.quote("<div class='video-frame' id='replacement'><video></video></div><p>Other page content</p>"));
            long end=SystemClock.elapsedRealtime()+10000;String focused;
            do{focused=eval(scenario,"!!document.querySelector('#replacement[data-vp-player]')");if("true".equals(focused))break;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<end);
            assertEquals("true",focused);scenario.onActivity(a->assertTrue((Boolean)field(a,"playerFullscreen")));
        }
    }
    @Test public void delayedPlayerIsStillDetectedAfterTheOldTimeout(){
        try(ActivityScenario<MainActivity> scenario=launch()){
            load(scenario,"<p>Waiting for player</p>");
            scenario.onActivity(a->{try{
                ((android.os.Handler)field(a,"sessionHandler")).removeCallbacksAndMessages(null);
                java.lang.reflect.Field scan=a.getClass().getDeclaredField("presentationScan");scan.setAccessible(true);scan.setInt(a,((Integer)scan.get(a))+1);
            }catch(Exception e){throw new AssertionError(e);}});
            eval(scenario,"document.body.innerHTML="+org.json.JSONObject.quote("<div class='video-frame'><video></video></div>"));
            scenario.onActivity(a->{try{java.lang.reflect.Method m=a.getClass().getDeclaredMethod("inspectPresentation",int.class,int.class,int.class);m.setAccessible(true);m.invoke(a,(Integer)field(a,"pageGeneration"),(Integer)field(a,"presentationScan"),61);}catch(Exception e){throw new AssertionError(e);}});
            await(scenario,a->(Boolean)field(a,"playerFullscreen"),"Player wait stopped after old timeout");
        }
    }
}

