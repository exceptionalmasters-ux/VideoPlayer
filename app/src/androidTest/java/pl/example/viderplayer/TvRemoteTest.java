package pl.example.viderplayer;

import android.app.Instrumentation;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Runs on an actual Android TV system image. All cards and forms are local fixtures. */
@RunWith(AndroidJUnit4.class)
public final class TvRemoteTest {
    @Before public void televisionOnly(){assumeTrue(TvUi.enabled(InstrumentationRegistry.getInstrumentation().getTargetContext()));}
    private static Object field(Object object,String name){
        try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}
    }
    private static void call(Object object,String method){
        try{Method m=object.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(object);}catch(Exception e){throw new AssertionError(e);}
    }
    private static void shell(Instrumentation instrumentation,String command) throws Exception {
        try(android.os.ParcelFileDescriptor descriptor=instrumentation.getUiAutomation().executeShellCommand(command);
            java.io.InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
            byte[] buffer=new byte[1024];while(stream.read(buffer)!=-1){ }
        }
    }
    private static void await(ActivityScenario<SearchActivity> scenario,java.util.function.Predicate<SearchActivity> condition,String message){
        java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
        long deadline=android.os.SystemClock.elapsedRealtime()+10000;
        do{scenario.onActivity(activity->ready.set(activity.hasWindowFocus()&&condition.test(activity)));if(ready.get())return;android.os.SystemClock.sleep(100);}while(android.os.SystemClock.elapsedRealtime()<deadline);
        scenario.onActivity(activity->{View focus=activity.getCurrentFocus();fail(message+"; windowFocus="+activity.hasWindowFocus()+"; focus="+(focus==null?"none":focus.getClass().getName()+" "+focus.getContentDescription()));});
    }
    private static void cardFocus(ActivityScenario<SearchActivity> scenario,int index){await(scenario,activity->((GridLayout)field(activity,"grid")).getChildAt(index).hasFocus(),"Expected focus on card "+index);}
    private static void key(int value){InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(value);InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    @Test public void tvLauncherAndDpadReachCardsAndOpenSelection() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        android.content.Context context=instrumentation.getTargetContext();
        Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(context.getPackageName());
        assertNotNull(context.getPackageManager().resolveActivity(launcher,0));
        assertTrue(context.getApplicationInfo().banner!=0);
        AtomicReference<Intent> opened=new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
                if(intent.getComponent()!=null&&MainActivity.class.getName().equals(intent.getComponent().getClassName())){
                    opened.set(new Intent(intent));return new Instrumentation.ActivityResult(0,null);
                }
                return null;
            }
        };
        instrumentation.addMonitor(monitor);
        try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(SearchActivity.class)){
            scenario.onActivity(activity->{
                try{
                    Object loader=field(activity,"thumbnails");Field stopped=loader.getClass().getDeclaredField("stopped");stopped.setAccessible(true);stopped.setBoolean(loader,true);
                    JSONArray rows=new JSONArray();
                    for(int i=0;i<12;i++)rows.put(new JSONObject().put("title","Film testowy "+(i+1)).put("url","https://freedisc.pl/tester,f-"+(100+i)+",test-mp4"));
                    Field f=activity.getClass().getDeclaredField("results");f.setAccessible(true);f.set(activity,rows);call(activity,"render");
                }catch(Exception e){throw new AssertionError(e);}
            });
            instrumentation.waitForIdleSync();
            await(scenario,activity->{GridLayout grid=(GridLayout)field(activity,"grid");return grid.isLaidOut()&&grid.getChildCount()==12&&grid.getChildAt(0).getWidth()>0;},"TV results must finish layout before remote input");
            scenario.onActivity(activity->{GridLayout grid=(GridLayout)field(activity,"grid");assertEquals(4,grid.getColumnCount());assertTrue(grid.getChildAt(0).requestFocus());});
            cardFocus(scenario,0);
            shell(instrumentation,"mkdir -p /sdcard/Download");
            shell(instrumentation,"screencap -p /sdcard/Download/videoplayer-tv.png");
            cardFocus(scenario,0);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            cardFocus(scenario,1);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            cardFocus(scenario,5);
            key(KeyEvent.KEYCODE_DPAD_UP);cardFocus(scenario,1);
            key(KeyEvent.KEYCODE_DPAD_LEFT);cardFocus(scenario,0);
            key(KeyEvent.KEYCODE_DPAD_UP);
            await(scenario,activity->find(activity.getWindow().getDecorView(),"Wyniki").hasFocus(),"UP must reach the collection buttons");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            await(scenario,activity->find(activity.getWindow().getDecorView(),"Historia").hasFocus(),"RIGHT must reach history");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            await(scenario,activity->find(activity.getWindow().getDecorView(),"Ulubione").hasFocus(),"RIGHT must reach favorites");
            key(KeyEvent.KEYCODE_DPAD_UP);
            await(scenario,activity->find(activity.getWindow().getDecorView(),"Raport").hasFocus(),"UP must reach the action row");
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            await(scenario,activity->find(activity.getWindow().getDecorView(),"Mam link").hasFocus(),"LEFT must reach paste link");
            key(KeyEvent.KEYCODE_DPAD_UP);
            await(scenario,activity->((View)field(activity,"query")).hasFocus(),"UP through native controls must reach title");
            scenario.onActivity(activity->{
                android.widget.EditText query=(android.widget.EditText)field(activity,"query");
                assertTrue("UP through toolbar rows must focus the search field",query.hasFocus());
                assertFalse("Focusing the title must not open the TV keyboard without OK",query.getShowSoftInputOnFocus());
            });
            key(KeyEvent.KEYCODE_DPAD_DOWN);cardFocus(scenario,0);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);cardFocus(scenario,1);
            // Gradle removes the test application's private files after instrumentation.
            // A shell-owned public fixture screenshot survives that cleanup for CI review.
            shell(instrumentation,"mkdir -p /sdcard/Download");
            shell(instrumentation,"screencap -p /sdcard/Download/videoplayer-tv.png");
            scenario.onActivity(activity->{
                View focus=activity.getCurrentFocus();
                assertTrue("RIGHT after returning from search must select the second card; focus="+(focus==null?"none":focus.getClass().getName()+" "+focus.getContentDescription()),((GridLayout)field(activity,"grid")).getChildAt(1).hasFocus());
            });
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            scenario.onActivity(activity->assertEquals("OK must activate the selected card",1,field(activity,"selectedResultIndex")));
            assertNotNull("OK must request the movie Activity",opened.get());
            assertEquals("https://freedisc.pl/tester,f-101,test-mp4",opened.get().getDataString());
        }finally{instrumentation.removeMonitor(monitor);}
    }
    private static Button find(View view,String text){
        if(view instanceof Button&&text.contentEquals(((Button)view).getText()))return (Button)view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button result=find(group.getChildAt(i),text);if(result!=null)return result;}}
        return null;
    }
    @Test public void nativeTvButtonCopiesAndExplicitlySubmitsTheManualCode() throws Exception {
        try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(SearchActivity.class)){
            String html="<!doctype html><html><body><form><input id='code' name='captcha_code'><button type='button' onclick=\"window.submitted=document.getElementById('code').value\">Wchodzę</button></form></body></html>";
            scenario.onActivity(activity->{
                WebView web=(WebView)field(activity,"engine");
                web.setWebViewClient(new android.webkit.WebViewClient());
                web.loadDataWithBaseURL("https://vider.info/search/all/test",html,"text/html","UTF-8","https://vider.info/search/all/test");
            });
            AtomicReference<String> loaded=new AtomicReference<>();long end=android.os.SystemClock.elapsedRealtime()+10000;
            do{scenario.onActivity(activity->((WebView)field(activity,"engine")).evaluateJavascript("!!document.getElementById('code')",loaded::set));android.os.SystemClock.sleep(100);}while(!"true".equals(loaded.get())&&android.os.SystemClock.elapsedRealtime()<end);
            assertEquals("true",loaded.get());
            scenario.onActivity(activity->{
                try{Method show=activity.getClass().getDeclaredMethod("showVerification",String.class);show.setAccessible(true);show.invoke(activity,"verification");}catch(Exception e){throw new AssertionError(e);}
                android.widget.EditText code=(android.widget.EditText)field(activity,"verificationCode");
                assertTrue("Challenge must focus its native editor",code.hasFocus());
                assertEquals("TV code must stay readable",android.graphics.Color.WHITE,code.getCurrentTextColor());
                code.setText("remote-code");
                Button button=find(activity.getWindow().getDecorView(),"Zatwierdź");assertNotNull(button);assertTrue(button.isFocusable());button.performClick();
            });
            AtomicReference<String> sent=new AtomicReference<>();end=android.os.SystemClock.elapsedRealtime()+10000;
            do{scenario.onActivity(activity->((WebView)field(activity,"engine")).evaluateJavascript("window.submitted||''",sent::set));android.os.SystemClock.sleep(100);}while(!"\"remote-code\"".equals(sent.get())&&android.os.SystemClock.elapsedRealtime()<end);
            assertEquals("\"remote-code\"",sent.get());
        }
    }
    private static void stopNetwork(WebView web){
        web.stopLoading();web.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view,android.webkit.WebResourceRequest request){return new android.webkit.WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream("<!doctype html><html><body></body></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        });
    }
    @Test public void physicalEnterStartsSearchWithoutAnotherClick(){
        try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(SearchActivity.class)){
            scenario.onActivity(activity->{stopNetwork((WebView)field(activity,"engine"));android.widget.EditText query=(android.widget.EditText)field(activity,"query");query.setText("Ronin");query.requestFocus();});
            key(KeyEvent.KEYCODE_ENTER);
            scenario.onActivity(activity->{assertTrue(((String)field(activity,"requestedSearch")).endsWith("/Ronin"));assertTrue((Boolean)field(activity,"searchRunning"));});
        }
    }
    @Test public void keyboardDoneStartsSearchAndTitleHasReadableColors(){
        try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(SearchActivity.class)){
            scenario.onActivity(activity->{stopNetwork((WebView)field(activity,"engine"));android.widget.EditText query=(android.widget.EditText)field(activity,"query");assertEquals(android.graphics.Color.WHITE,query.getCurrentTextColor());query.setText("Maverick");query.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);assertTrue(((String)field(activity,"requestedSearch")).endsWith("/Maverick"));assertTrue((Boolean)field(activity,"searchRunning"));});
        }
    }
    private static String evalMovie(ActivityScenario<MainActivity> scenario,String script){
        AtomicReference<String> value=new AtomicReference<>();scenario.onActivity(activity->((WebView)field(activity,"web")).evaluateJavascript(script,value::set));long end=android.os.SystemClock.elapsedRealtime()+5000;
        while(value.get()==null&&android.os.SystemClock.elapsedRealtime()<end)android.os.SystemClock.sleep(50);assertNotNull(value.get());return value.get();
    }
    @Test public void compactPlayerUsesRemoteMediaAndVisibleCursorClicksThePage() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String url="https://vider.info/vid/+fcursortest";
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(url)))){
            String html="<!doctype html><html><body><div class='video-frame'><video></video><button id='target' style='position:fixed;inset:0;width:100vw;height:100vh' onclick='window.clicked=true'>Klik</button></div><script>window.commands=[];window.__vpTvCommand=function(action){window.commands.push(action);return true;};</script></body></html>";
            scenario.onActivity(activity->{WebView web=(WebView)field(activity,"web");web.stopLoading();web.setWebViewClient(new android.webkit.WebViewClient(){
                @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view,android.webkit.WebResourceRequest request){return new android.webkit.WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
            });web.loadUrl(url+"?fixture=cursor");});
            long end=android.os.SystemClock.elapsedRealtime()+15000;while(!"true".equals(evalMovie(scenario,"!!document.getElementById('target')"))&&android.os.SystemClock.elapsedRealtime()<end)android.os.SystemClock.sleep(100);
            assertEquals("true",evalMovie(scenario,"!!document.getElementById('target')"));
            scenario.onActivity(activity->{View root=activity.getWindow().getDecorView();assertNull(find(root,"−10 s"));assertNull(find(root,"+10 s"));assertNull(find(root,"Play / Pauza"));Button movie=find(root,"Widok filmu");assertTrue(movie.isShown());assertEquals(android.graphics.Color.WHITE,movie.getCurrentTextColor());find(root,"Kursor").performClick();assertTrue(((TvPointer)field(activity,"pointer")).active());assertTrue(((TvPointer)field(activity,"pointer")).isShown());});
            AtomicReference<Float> x=new AtomicReference<>();scenario.onActivity(activity->x.set((Float)field(field(activity,"pointer"),"x")));
            key(KeyEvent.KEYCODE_DPAD_RIGHT);scenario.onActivity(activity->assertTrue((Float)field(field(activity,"pointer"),"x")>x.get()));
            shell(InstrumentationRegistry.getInstrumentation(),"mkdir -p /sdcard/Download");
            shell(InstrumentationRegistry.getInstrumentation(),"screencap -p /sdcard/Download/videoplayer-tv-cursor.png");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            end=android.os.SystemClock.elapsedRealtime()+5000;while(!"true".equals(evalMovie(scenario,"window.clicked===true"))&&android.os.SystemClock.elapsedRealtime()<end)android.os.SystemClock.sleep(100);
            assertEquals("true",evalMovie(scenario,"window.clicked===true"));
            key(KeyEvent.KEYCODE_BACK);scenario.onActivity(activity->{assertFalse(((TvPointer)field(activity,"pointer")).active());assertFalse(activity.isFinishing());find(activity.getWindow().getDecorView(),"Widok filmu").performClick();});
            end=android.os.SystemClock.elapsedRealtime()+10000;AtomicReference<Boolean> full=new AtomicReference<>(false);do{scenario.onActivity(activity->full.set((Boolean)field(activity,"playerFullscreen")));if(full.get())break;android.os.SystemClock.sleep(100);}while(android.os.SystemClock.elapsedRealtime()<end);assertTrue(full.get());
            key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);
            assertEquals("[\"back\",\"forward\",\"toggle\"]",new org.json.JSONTokener(evalMovie(scenario,"JSON.stringify(window.commands)")).nextValue());
            key(KeyEvent.KEYCODE_DPAD_UP);scenario.onActivity(activity->{assertFalse((Boolean)field(activity,"playerFullscreen"));assertTrue(find(activity.getWindow().getDecorView(),"Widok filmu").hasFocus());});
        }
    }
}
