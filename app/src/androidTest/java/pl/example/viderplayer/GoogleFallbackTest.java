package pl.example.viderplayer;
import android.content.*;
import android.net.Uri;
import android.os.SystemClock;
import android.webkit.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class GoogleFallbackTest {
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void call(Object o,String method){try{Method m=o.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void await(ActivityScenario<SearchActivity> scenario,Predicate<SearchActivity> condition,String message){AtomicBoolean done=new AtomicBoolean();long end=SystemClock.elapsedRealtime()+15000;do{scenario.onActivity(a->done.set(condition.test(a)));if(done.get())return;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<end);fail(message);}
    private static GoogleFallback google(SearchActivity a){return (GoogleFallback)field(a,"google");}
    private static void fixture(WebView view,Function<String,String> html){WebViewClient original=view.getWebViewClient();view.setWebViewClient(new WebViewClient(){
        @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream(html.apply(request.getUrl().toString()).getBytes(StandardCharsets.UTF_8)));}
        @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap b){original.onPageStarted(v,url,b);}
        @Override public void onPageFinished(WebView v,String url){original.onPageFinished(v,url);}
    });}
    private static void startFallback(SearchActivity a,Function<String,String> html){
        ((Spinner)field(a,"sourcePicker")).setSelection(SiteSource.FREEDISC.ordinal());
        try{Method select=SearchActivity.class.getDeclaredMethod("selectSource",SiteSource.class);select.setAccessible(true);select.invoke(a,SiteSource.FREEDISC);}catch(Exception e){throw new AssertionError(e);}
        Object loader=field(a,"thumbnails");try{Field f=loader.getClass().getDeclaredField("stopped");f.setAccessible(true);f.setBoolean(loader,true);}catch(Exception e){throw new AssertionError(e);}
        fixture((WebView)field(a,"engine"),url->"<body>Loading</body>");
        ((EditText)field(a,"query")).setText("Ronin");call(a,"search");Runnable timeout=(Runnable)field(a,"searchTimeout");assertNotNull(timeout);timeout.run();assertNotNull(google(a));fixture(google(a).engine,html);
    }
    private static String cards(){return "<!doctype html><body><a href='https://freedisc.pl/embed/video/8307530?match=1'><h3>Ronin 1998</h3></a><a href='https://freedisc.pl.evil.test/u,f-99,film-mp4'><h3>Wrong domain</h3></a><a href='https://vider.info/vid/+fwrong'><h3>Wrong source</h3></a></body>";}
    @Test public void timedOutProviderReturnsOnlySelectedSourceNativeCardsAndOpensTheEmbed(){
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();AtomicReference<Intent> opened=new AtomicReference<>();
        try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(SearchActivity.class)){
            scenario.onActivity(a->startFallback(a,url->cards()));
            await(scenario,a->((GridLayout)field(a,"grid")).getChildCount()==1,"Google did not return the one allowed native card");
            scenario.onActivity(a->{assertFalse((Boolean)field(a,"searchRunning"));assertNull(google(a));assertTrue(((TextView)field(a,"status")).getText().toString().contains("Google"));});
            android.app.Instrumentation.ActivityMonitor monitor=new android.app.Instrumentation.ActivityMonitor(){@Override public android.app.Instrumentation.ActivityResult onStartActivity(Intent intent){if(intent.getComponent()!=null&&MainActivity.class.getName().equals(intent.getComponent().getClassName())){opened.set(new Intent(intent));return new android.app.Instrumentation.ActivityResult(0,null);}return null;}};
            instrumentation.addMonitor(monitor);try{scenario.onActivity(a->((GridLayout)field(a,"grid")).getChildAt(0).performClick());instrumentation.waitForIdleSync();assertNotNull(opened.get());assertEquals("https://freedisc.pl/embed/video/8307530?match=1",opened.get().getDataString());}finally{instrumentation.removeMonitor(monitor);}
        }finally{new LocalLibrary(InstrumentationRegistry.getInstrumentation().getTargetContext()).remove("history","https://freedisc.pl/embed/video/8307530");}
    }
    @Test public void googleVerificationWaitsWithoutDeadlineAndKeepsTheOriginalQuery(){
        String target=GoogleSearchPolicy.url("Ronin",SiteSource.FREEDISC);
        String form="<!doctype html><body><form id='captcha-form'><input id='manual_code'><button type='button' onclick=\"if(document.getElementById('manual_code').value==='manual'){history.replaceState({},'',"+JSONObject.quote(target).replace("\"","&quot;")+");document.body.innerHTML="+JSONObject.quote(cards()).replace("\"","&quot;")+"}\">Continue</button></form></body>";
        try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(SearchActivity.class)){
            scenario.onActivity(a->startFallback(a,url->form));
            await(scenario,a->google(a)!=null&&google(a).accessOpen()&&google(a).engine.isLaidOut(),"Google verification did not open in the app");
            scenario.onActivity(a->{if(TvUi.enabled(a)){GoogleFallback worker=google(a);worker.key(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_MENU));assertTrue(((TvPointer)field(worker,"pointer")).active());worker.key(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_MENU));assertFalse(((TvPointer)field(worker,"pointer")).active());}assertTrue((Boolean)field(a,"searchRunning"));((EditText)field(a,"query")).setText("Changed draft");try{Field f=GoogleFallback.class.getDeclaredField("deadline");f.setAccessible(true);f.setLong(google(a),SystemClock.elapsedRealtime()-1);}catch(Exception e){throw new AssertionError(e);}});
            SystemClock.sleep(1500);scenario.onActivity(a->{assertNotNull(google(a));assertTrue(google(a).accessOpen());google(a).engine.evaluateJavascript("document.getElementById('manual_code').value='manual';document.querySelector('button').click();",null);});
            await(scenario,a->((GridLayout)field(a,"grid")).getChildCount()==1,"Manual Google confirmation did not resume the original query");
            scenario.onActivity(a->assertTrue(((TextView)field(a,"status")).getText().toString().contains("Google")));
        }
    }
    @Test public void failedGoogleSearchStopsInsteadOfLoopingToTheProvider(){
        try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(SearchActivity.class)){
            AtomicReference<GoogleFallback> old=new AtomicReference<>();
            scenario.onActivity(a->{startFallback(a,url->"<body>Unrecognized page</body>");old.set(google(a));});
            SystemClock.sleep(500);
            scenario.onActivity(a->{try{Field f=GoogleFallback.class.getDeclaredField("deadline");f.setAccessible(true);f.setLong(google(a),SystemClock.elapsedRealtime()-1);}catch(Exception e){throw new AssertionError(e);}});
            await(scenario,a->!((Boolean)field(a,"searchRunning")),"Failed Google fallback kept searching");
            scenario.onActivity(a->{assertNull(google(a));assertTrue((Boolean)field(a,"fallbackUsed"));assertEquals(0,((GridLayout)field(a,"grid")).getChildCount());});
        }
    }
    @Test public void directFreediscEmbedLaunchUsesTheMovieFullscreenAndHistoryKey(){
        String url="https://freedisc.pl/embed/video/8307530?match=1";
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(url)).putExtra("manualStart",true);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(intent)){
            scenario.onActivity(a->{WebView web=(WebView)field(a,"web");web.stopLoading();fixture(web,requested->"<!doctype html><body><div class='jwplayer'><video></video></div></body>");web.loadUrl(url);});
            AtomicBoolean full=new AtomicBoolean();long end=SystemClock.elapsedRealtime()+15000;do{scenario.onActivity(a->full.set((Boolean)field(a,"playerFullscreen")));if(full.get())break;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<end);
            assertTrue("Embed did not enter fullscreen",full.get());scenario.onActivity(a->{assertEquals(url,((MovieNavigation)field(a,"movieNavigation")).target());assertNotNull(((LocalLibrary)field(a,"library")).find("https://freedisc.pl/u,f-8307530,ronin-mp4"));});
        }finally{new LocalLibrary(InstrumentationRegistry.getInstrumentation().getTargetContext()).remove("history",url);}
    }
}
