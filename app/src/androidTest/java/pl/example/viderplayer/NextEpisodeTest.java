package pl.example.viderplayer;

import android.app.Instrumentation;
import android.content.*;
import android.net.Uri;
import android.os.SystemClock;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.json.JSONObject;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class NextEpisodeTest {
    private static final String URL="https://vider.info/vid/+fepisodefixture",TITLE="Serial Testowy S01E01";
    private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private static EpisodeSuggestions ui(MainActivity activity){return (EpisodeSuggestions)field(activity,"episodes");}
    private static void await(ActivityScenario<MainActivity> scenario,Predicate<MainActivity> condition,String message){
        AtomicBoolean done=new AtomicBoolean();long end=SystemClock.elapsedRealtime()+15000;
        do{scenario.onActivity(a->done.set(condition.test(a)));if(done.get())return;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<end);
        scenario.onActivity(a->fail(message+" · "+((DiagnosticLog)field(a,"diagnostics")).export()));
    }
    private static String eval(ActivityScenario<MainActivity> scenario,String code){
        AtomicReference<String> value=new AtomicReference<>();scenario.onActivity(a->((WebView)field(a,"web")).evaluateJavascript(code,value::set));
        long end=SystemClock.elapsedRealtime()+5000;while(value.get()==null&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);assertNotNull(value.get());return value.get();
    }
    private static ActivityScenario<MainActivity> launch(){return launchAt(URL);}
    private static ActivityScenario<MainActivity> launchAt(String movieUrl){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(movieUrl)).putExtra("movieTitle",TITLE));
        scenario.onActivity(a->{
            WebView movie=(WebView)field(a,"web");movie.stopLoading();WebViewClient original=movie.getWebViewClient();
            movie.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return html("<body data-next-fixture='movie'><div class='video-frame'><video></video></div></body>");}
                @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap b){original.onPageStarted(v,u,b);}
                @Override public void onPageFinished(WebView v,String u){original.onPageFinished(v,u);}
            });movie.loadUrl(movieUrl+"?fixture="+java.util.UUID.randomUUID());
        });
        long end=SystemClock.elapsedRealtime()+15000;
        while(!"true".equals(eval(scenario,"!!document.querySelector('[data-next-fixture=movie]')"))){if(SystemClock.elapsedRealtime()>end)fail("Movie fixture missing");SystemClock.sleep(100);}
        scenario.onActivity(a->{
            ui(a).observe(movieUrl,TITLE,0,100,false);
            Object loader=field(ui(a),"thumbnails");try{Field f=loader.getClass().getDeclaredField("stopped");f.setAccessible(true);f.setBoolean(loader,true);}catch(Exception e){throw new AssertionError(e);}
        });
        return scenario;
    }
    private static WebResourceResponse html(String body){return new WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream(("<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"+body+"</html>").getBytes(StandardCharsets.UTF_8)));}
    private static String cards(String episode){return "<div id='search_files_result'><a href='/vid/+fnextone' title='Serial Testowy "+episode+" 720p'>Serial Testowy "+episode+" 720p</a><a href='/vid/+fnexttwo' title='Serial Testowy "+episode+" 1080p'>Serial Testowy "+episode+" 1080p</a><a href='/vid/+fwrong' title='Inny Serial "+episode+"'>Inny Serial "+episode+"</a></div>";}
    private static void provider(ActivityScenario<MainActivity> scenario,Function<String,String> content,ConcurrentLinkedQueue<String> requests){
        scenario.onActivity(a->{
            WebView engine=ui(a).search().engine;WebViewClient original=engine.getWebViewClient();
            engine.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){String path=r.getUrl().getPath();if(r.isForMainFrame())requests.add(path);return html(content.apply(path));}
                @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap b){original.onPageStarted(v,u,b);}
                @Override public void onPageFinished(WebView v,String u){original.onPageFinished(v,u);}
            });
        });
    }
    private static void snapshot(String name){
        try{for(String command:new String[]{"mkdir -p /sdcard/Download","screencap -p /sdcard/Download/"+name}){
            android.os.ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
            try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}
        }}catch(Exception e){throw new AssertionError(e);}
    }
    private static WebResourceRequest request(String url,boolean main){return new WebResourceRequest(){
        @Override public Uri getUrl(){return Uri.parse(url);}
        @Override public boolean isForMainFrame(){return main;}
        @Override public boolean isRedirect(){return false;}
        @Override public boolean hasGesture(){return false;}
        @Override public String getMethod(){return "GET";}
        @Override public java.util.Map<String,String> getRequestHeaders(){return java.util.Collections.emptyMap();}
    };}
    @Test public void verificationKeepsHttpsFramesWhileBlockingExternalMainNavigation(){
        try(ActivityScenario<MainActivity> scenario=launch()){
            scenario.onActivity(a->{WebView view=ui(a).search().engine;WebViewClient client=view.getWebViewClient();
                assertFalse(client.shouldOverrideUrlLoading(view,request("https://www.google.com/recaptcha/api2/anchor",false)));
                assertTrue(client.shouldOverrideUrlLoading(view,request("intent://external-app",false)));
                assertTrue(client.shouldOverrideUrlLoading(view,request("https://www.google.com/recaptcha/api2/anchor",true)));
            });
        }finally{cleanup();}
    }
    private static void cleanup(){LocalLibrary l=new LocalLibrary(InstrumentationRegistry.getInstrumentation().getTargetContext());l.remove("history",URL);l.remove("history","https://freedisc.pl/u,f-56789,serial-testowy-s01e01-mp4");l.remove("history","https://vider.info/vid/+fnextone");}
    @Test public void prefetchIsSilentThenEndShowsOnlyTheRightEpisodeAndOkOpensTheCard(){
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();AtomicReference<Intent> opened=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=launch()){
            ConcurrentLinkedQueue<String> requests=new ConcurrentLinkedQueue<>();provider(scenario,path->cards("S01E02"),requests);
            scenario.onActivity(a->ui(a).observe(URL,TITLE,59,100,false));assertTrue(requests.isEmpty());
            scenario.onActivity(a->ui(a).observe(URL,TITLE,60,100,false));
            await(scenario,a->ui(a).flow.stage==EpisodeFlow.Stage.READY,"Next episode was not prefetched");
            scenario.onActivity(a->{assertNull(ui(a).panel);assertNull(ui(a).accessPanel);assertEquals(2,ui(a).rows.length());assertFalse(ui(a).flow.seasonFallback);ui(a).observe(URL,TITLE,89,100,false);assertNull(ui(a).panel);ui(a).observe(URL,TITLE,90,100,false);assertFalse(ui(a).flow.ended);});
            await(scenario,a->ui(a).panel!=null&&ui(a).panel.isLaidOut()&&(!TvUi.enabled(a)||ui(a).cards.getChildAt(0).hasFocus()),"End overlay or first-card focus missing");
            snapshot("videoplayer-next-episode.png");
            assertEquals("true",eval(scenario,"!!document.querySelector('[data-next-fixture=movie]')"));
            Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(intent.getComponent()!=null&&MainActivity.class.getName().equals(intent.getComponent().getClassName())){opened.set(new Intent(intent));return new Instrumentation.ActivityResult(0,null);}return null;}};
            instrumentation.addMonitor(monitor);
            try{
                scenario.onActivity(a->{assertEquals(2,ui(a).cards.getChildCount());if(!TvUi.enabled(a))ui(a).cards.getChildAt(0).performClick();});
                AtomicBoolean tv=new AtomicBoolean();if(opened.get()==null){scenario.onActivity(a->tv.set(TvUi.enabled(a)));if(tv.get())instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);}
                instrumentation.waitForIdleSync();assertNotNull("Next card did not open",opened.get());assertEquals("https://vider.info/vid/+fnextone",opened.get().getDataString());assertTrue(opened.get().getBooleanExtra("nextEpisodeAutoPlay",false));
            }finally{instrumentation.removeMonitor(monitor);}
            assertEquals(1,requests.size());
        }finally{cleanup();}
    }
    @Test public void aRealEmptyResultFallsBackOnceToTheFirstEpisodeOfTheNextSeason(){
        try(ActivityScenario<MainActivity> scenario=launch()){
            ConcurrentLinkedQueue<String> requests=new ConcurrentLinkedQueue<>();provider(scenario,path->path.contains("S01E02")?"<div id='search_files_result'>Brak wyników wyszukiwania dla frazy</div>":cards("S02E01"),requests);
            scenario.onActivity(a->ui(a).observe(URL,TITLE,70,100,false));await(scenario,a->ui(a).flow.stage==EpisodeFlow.Stage.READY,"Next season was not found");
            scenario.onActivity(a->{assertTrue(ui(a).flow.seasonFallback);assertEquals("Serial Testowy S02E01",ui(a).flow.target.query());assertNull(ui(a).panel);ui(a).observe(URL,TITLE,100,100,true);});
            await(scenario,a->ui(a).panel!=null,"Next-season overlay missing");assertEquals(2,requests.size());
            scenario.onActivity(a->{a.onBackPressed();assertNull(ui(a).panel);assertFalse(a.isFinishing());ui(a).observe(URL,TITLE,100,100,true);assertNull("Repeated end must not reopen a dismissed overlay",ui(a).panel);});
        }finally{cleanup();}
    }
    @Test public void manualCodeWaitsWithoutTimeoutOrSeasonFallbackAndDoneResumesTheSameSearch(){
        String rows=cards("S01E02");
        String challenge="<form id='challenge'><p>Wpisz kod z obrazka</p><input id='captcha_code' name='captcha_code' style='height:50px'><button type='button' onclick=\"if(document.getElementById('captcha_code').value==='manual-code'){document.getElementById('challenge').remove();document.body.insertAdjacentHTML('beforeend',"+JSONObject.quote(rows).replace("\"","&quot;")+")}\">Wchodzę</button></form>";
        try(ActivityScenario<MainActivity> scenario=launch()){
            ConcurrentLinkedQueue<String> requests=new ConcurrentLinkedQueue<>();provider(scenario,path->challenge,requests);
            scenario.onActivity(a->ui(a).observe(URL,TITLE,70,100,false));await(scenario,a->ui(a).flow.stage==EpisodeFlow.Stage.ACCESS,"Manual code was not detected");
            scenario.onActivity(a->{assertNull(ui(a).accessPanel);assertEquals(View.VISIBLE,ui(a).notice.getVisibility());try{Field f=EpisodeSearch.class.getDeclaredField("deadline");f.setAccessible(true);f.setLong(ui(a).worker,SystemClock.elapsedRealtime()-60000);}catch(Exception e){throw new AssertionError(e);}});
            SystemClock.sleep(1600);scenario.onActivity(a->{assertEquals(EpisodeFlow.Stage.ACCESS,ui(a).flow.stage);assertFalse(ui(a).flow.seasonFallback);ui(a).observe(URL,TITLE,100,100,true);});
            await(scenario,a->ui(a).accessPanel!=null&&ui(a).code!=null&&ui(a).code.isShown()&&ui(a).code.hasFocus(),"End must show and focus the manual code field");
            scenario.onActivity(a->{ui(a).code.setText("manual-");a.onBackPressed();assertNull(ui(a).accessPanel);assertEquals(View.VISIBLE,ui(a).notice.getVisibility());assertEquals(EpisodeFlow.Stage.ACCESS,ui(a).flow.stage);ui(a).notice.performClick();});
            await(scenario,a->ui(a).code!=null&&ui(a).code.isShown()&&ui(a).code.hasFocus(),"Waiting code must be reachable again after returning to the film");
            snapshot("videoplayer-next-code.png");
            scenario.onActivity(a->{assertEquals("manual-",ui(a).code.getText().toString());ui(a).code.setText("manual-code");ui(a).code.onEditorAction(EditorInfo.IME_ACTION_DONE);});
            await(scenario,a->ui(a).flow.stage==EpisodeFlow.Stage.READY&&ui(a).panel!=null&&ui(a).accessPanel==null,"Manual confirmation did not resume the same episode query");
            scenario.onActivity(a->{assertEquals("Serial Testowy S01E02",ui(a).flow.target.query());assertFalse(ui(a).flow.seasonFallback);assertEquals(2,ui(a).rows.length());});
            assertEquals(1,requests.size());assertEquals("true",eval(scenario,"!!document.querySelector('[data-next-fixture=movie]')"));
        }finally{cleanup();}
    }
    @Test public void anUnfinishedSearchIsAnErrorAndNeverASeasonBoundary(){
        try(ActivityScenario<MainActivity> scenario=launch()){
            ConcurrentLinkedQueue<String> requests=new ConcurrentLinkedQueue<>();provider(scenario,path->"<div id='search_files_result'><i class='icon-spinner'>…</i> Proszę czekać, szukam...</div>",requests);
            scenario.onActivity(a->ui(a).observe(URL,TITLE,70,100,false));await(scenario,a->ui(a).worker.engine.getProgress()==100&&!requests.isEmpty(),"Search fixture missing");
            scenario.onActivity(a->{try{Field f=EpisodeSearch.class.getDeclaredField("deadline");f.setAccessible(true);f.setLong(ui(a).worker,SystemClock.elapsedRealtime()-1);}catch(Exception e){throw new AssertionError(e);}});
            await(scenario,a->ui(a).worker.google!=null,"Unfinished provider search did not try Google");
            scenario.onActivity(a->{try{java.lang.reflect.Method fail=GoogleFallback.class.getDeclaredMethod("fail",String.class);fail.setAccessible(true);fail.invoke(ui(a).worker.google,"Google unavailable fixture");}catch(Exception e){throw new AssertionError(e);}});
            await(scenario,a->ui(a).flow.stage==EpisodeFlow.Stage.FAILED,"Failed Google fallback did not stop with an error");
            scenario.onActivity(a->{assertFalse(ui(a).flow.seasonFallback);assertEquals("Serial Testowy S01E02",ui(a).flow.target.query());});assertEquals(1,requests.size());
        }finally{cleanup();}
    }

    @Test public void freediscAccessReturningHomeRestartsTheSameEpisodeRouteAfterManualConfirmation(){
        String movie="https://freedisc.pl/u,f-56789,serial-testowy-s01e01-mp4";
        String results="<div id='search_files_result'><div class='search-element-container'><a title='Serial Testowy S01E02.mp4' href='/u,f-56800,serial-testowy-s01e02-mp4'>Serial Testowy S01E02.mp4</a></div></div>";
        String form="<form><input id='captcha_code' name='captcha_code' style='height:50px'><button type='button' onclick=\"if(document.getElementById('captcha_code').value==='manual-code'){window.verified=true;history.replaceState({},'', '/');document.body.innerHTML='Start'}\">Wchodzę</button></form>";
        String home="<p>Start</p><script>window.paths=[];window.verified=false;window.App={Instance:{Search:{}}};window.Router={navigate:function(path,trigger){paths.push(path);history.pushState({},'',path);document.body.innerHTML=window.verified?"+JSONObject.quote(results)+":"+JSONObject.quote(form)+"}};</script>";
        try(ActivityScenario<MainActivity> scenario=launchAt(movie)){
            ConcurrentLinkedQueue<String> requests=new ConcurrentLinkedQueue<>();provider(scenario,path->home,requests);
            scenario.onActivity(a->ui(a).observe(movie,TITLE,70,100,false));await(scenario,a->ui(a).flow.stage==EpisodeFlow.Stage.ACCESS,"Freedisc challenge missing");
            scenario.onActivity(a->{assertFalse(ui(a).flow.seasonFallback);ui(a).observe(movie,TITLE,100,100,true);});
            await(scenario,a->ui(a).code!=null&&ui(a).code.isShown()&&ui(a).code.hasFocus(),"Freedisc native code editor missing");
            scenario.onActivity(a->{ui(a).code.setText("manual-code");ui(a).code.onEditorAction(EditorInfo.IME_ACTION_DONE);});
            await(scenario,a->ui(a).flow.stage==EpisodeFlow.Stage.READY&&ui(a).panel!=null,"Freedisc did not restore the prepared query after returning home");
            scenario.onActivity(a->{assertEquals("Serial Testowy S01E02",ui(a).flow.target.query());assertFalse(ui(a).flow.seasonFallback);assertEquals(1,ui(a).rows.length());});
            assertEquals(1,requests.size());
        }finally{cleanup();}
    }
}
