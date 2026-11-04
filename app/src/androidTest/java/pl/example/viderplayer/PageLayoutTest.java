package pl.example.viderplayer;

import android.os.SystemClock;
import android.webkit.WebView;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real WebView geometry with local HTML only, including a fixed-width embedded player. */
@RunWith(AndroidJUnit4.class)
public final class PageLayoutTest {
    private static Object field(Object target,String name) {
        try { Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target); }
        catch(Exception e){throw new AssertionError(e);}
    }
    private static String asset(String name) throws Exception {
        try(java.io.InputStream in=InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets().open(name);
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] bytes=new byte[4096];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private static WebView web(SearchActivity activity){return (WebView)field(activity,"engine");}
    private static String eval(ActivityScenario<SearchActivity> scenario,String script) {
        AtomicReference<String> value=new AtomicReference<>();
        scenario.onActivity(activity->web(activity).evaluateJavascript(script,value::set));
        long end=SystemClock.elapsedRealtime()+5000;
        while(value.get()==null&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);
        assertNotNull("JavaScript did not complete",value.get());return value.get();
    }
    private static void awaitTrue(ActivityScenario<SearchActivity> scenario,String script) {
        long end=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<end){if("true".equals(eval(scenario,script)))return;SystemClock.sleep(100);}
        fail("Condition did not become true: "+script);
    }
    private static void load(ActivityScenario<SearchActivity> scenario,String html) { load(scenario,html,""); }
    private static void load(ActivityScenario<SearchActivity> scenario,String html,String child) {
        scenario.onActivity(activity->{
            web(activity).setWebViewClient(new android.webkit.WebViewClient() {
                @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view,android.webkit.WebResourceRequest request) {
                    return new android.webkit.WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream(child.getBytes(StandardCharsets.UTF_8)));
                }
            });
            web(activity).getSettings().setUseWideViewPort(true);
            web(activity).getSettings().setLoadWithOverviewMode(true);
            ((View)field(activity,"resultsView")).setVisibility(View.GONE);
            web(activity).setVisibility(View.VISIBLE);
            web(activity).loadDataWithBaseURL("https://freedisc.pl/local-layout",html,"text/html","UTF-8","https://freedisc.pl/local-layout");
        });
        awaitTrue(scenario,"document.readyState==='complete' && !!(document.getElementById('movie') || document.getElementById('code'))");
    }
    @Test public void embeddedPlayerFitsBothDocumentsAndRestoresWithoutReload() throws Exception {
        String inner="<!doctype html><html><head><meta name='viewport' content='width=1000'><script>"+asset("frame-fit.js")
            +"</script></head><body><div class='jwplayer' style='width:1000px;height:562px'><div class='jw-media' style='width:100%;height:100%'><video style='width:1000px;height:562px'></video></div></div></body></html>";
        String html="<!doctype html><html><head><meta name='viewport' content='width=1000'></head><body><div class='video-frame' style='width:1000px;height:562px'>"
            +"<iframe id='movie' onload='window.frameLoads=(window.frameLoads||0)+1' style='width:1000px;height:562px;border:0' src='https://freedisc.pl/embed/video/test'></iframe></div><div id='comments'>Comments</div></body></html>";
        try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(SearchActivity.class)) {
            load(scenario,html,inner);
            awaitTrue(scenario,"!!document.getElementById('movie')&&!!document.getElementById('movie').contentDocument.querySelector('video')");
            String focus=asset("focus-player.js");
            assertEquals("\"player\"",eval(scenario,focus));
            scenario.onActivity(activity->{web(activity).getSettings().setUseWideViewPort(false);web(activity).getSettings().setLoadWithOverviewMode(false);});
            awaitTrue(scenario,"!!document.getElementById('movie').contentDocument.getElementById('vp-frame-fit-style')");
            String metrics="(function(){const f=document.getElementById('movie'),v=f.contentDocument.querySelector('video');return JSON.stringify({outer:f.getBoundingClientRect().width,viewport:innerWidth,inner:v.getBoundingClientRect().width,frameWidth:f.contentWindow.innerWidth,fit:f.contentWindow.getComputedStyle(v).objectFit,loads:window.frameLoads});}())";
            JSONObject value=new JSONObject((String)new JSONTokener(eval(scenario,metrics)).nextValue());
            assertTrue(value.toString(),value.getDouble("outer")<=value.getDouble("viewport")+2);
            assertTrue(value.toString(),value.getDouble("inner")<=value.getDouble("frameWidth")+2);
            assertEquals("contain",value.getString("fit"));
            int loads=value.getInt("loads");
            assertEquals("\"page\"",eval(scenario,focus));
            awaitTrue(scenario,"!document.getElementById('movie').contentDocument.getElementById('vp-frame-fit-style')");
            assertEquals("\"width=1000\"",eval(scenario,"document.querySelector('meta[name=viewport]').content"));
            assertEquals(Integer.toString(loads),eval(scenario,"window.frameLoads"));
        }
    }
    @Test public void verificationFormFitsPhoneAndPreservesEnteredCode() throws Exception {
        String html="<!doctype html><html><head><meta name='viewport' content='width=1000'></head><body style='min-width:1000px'>"
            +"<div style='width:1000px;margin-left:300px'><form style='width:900px'><p>Kod z obrazka</p>"
            +"<input name='captcha_code' id='code' value='typed-by-user' style='width:800px;font-size:9px'><button>Wchodzę</button>"
            +"</form></div></body></html>";
        try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(SearchActivity.class)) {
            load(scenario,html);
            scenario.onActivity(activity->{web(activity).getSettings().setUseWideViewPort(false);web(activity).getSettings().setLoadWithOverviewMode(false);});
            assertEquals("true",eval(scenario,asset("verification-layout.js")));
            awaitTrue(scenario,"document.getElementById('code').getBoundingClientRect().right<=innerWidth+2");
            JSONObject value=new JSONObject((String)new JSONTokener(eval(scenario,"(function(){const e=document.getElementById('code');return JSON.stringify({left:e.getBoundingClientRect().left,width:e.getBoundingClientRect().width,font:parseFloat(getComputedStyle(e).fontSize),value:e.value});}())")).nextValue());
            assertTrue(value.toString(),value.getDouble("left")>=0);
            assertTrue(value.toString(),value.getDouble("width")>=200);
            assertTrue(value.toString(),value.getDouble("font")>=20);
            assertEquals("typed-by-user",value.getString("value"));
            eval(scenario,"document.getElementById('code').remove()");
            assertEquals("false",eval(scenario,asset("verification-layout.js")));
            assertEquals("\"width=1000\"",eval(scenario,"document.querySelector('meta[name=viewport]').content"));
        }
    }
}
