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
            scenario.onActivity(activity->{GridLayout grid=(GridLayout)field(activity,"grid");assertEquals(4,grid.getColumnCount());grid.getChildAt(0).requestFocus();});
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            scenario.onActivity(activity->assertTrue(((GridLayout)field(activity,"grid")).getChildAt(1).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            scenario.onActivity(activity->assertTrue(((GridLayout)field(activity,"grid")).getChildAt(5).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_UP);
            scenario.onActivity(activity->{
                android.widget.EditText query=(android.widget.EditText)field(activity,"query");
                assertTrue("UP from the first row must focus the search field",query.hasFocus());
                assertFalse("Focusing the title must not open the TV keyboard without OK",query.getShowSoftInputOnFocus());
            });
            key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_RIGHT);
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
                ((android.widget.EditText)field(activity,"verificationCode")).setText("remote-code");
                Button button=find(activity.getWindow().getDecorView(),"Zatwierdź");assertNotNull(button);assertTrue(button.isFocusable());button.performClick();
            });
            AtomicReference<String> sent=new AtomicReference<>();end=android.os.SystemClock.elapsedRealtime()+10000;
            do{scenario.onActivity(activity->((WebView)field(activity,"engine")).evaluateJavascript("window.submitted||''",sent::set));android.os.SystemClock.sleep(100);}while(!"\"remote-code\"".equals(sent.get())&&android.os.SystemClock.elapsedRealtime()<end);
            assertEquals("\"remote-code\"",sent.get());
        }
    }
}
