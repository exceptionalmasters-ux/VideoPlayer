package pl.example.viderplayer;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Local HTML fixture only. No request to either service and no real CAPTCHA is read or solved. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 30)
public final class VerificationKeyboardTest {
    private static final String FIXTURE = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
        + "<body><label for='code'>Kod testowy</label><input style='font-size:24px;width:90%' id='code' name='captcha_code' type='text' autocomplete='off'>"
        + "<button type='button'>Przycisk testowy</button></body></html>";

    private static Object field(Object target, String name) {
        try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static WebView engine(SearchActivity activity) { return (WebView) field(activity, "engine"); }
    private static void selectSource(SearchActivity activity, SiteSource source) {
        try {
            ((android.widget.Spinner) field(activity, "sourcePicker")).setSelection(source.ordinal());
            java.lang.reflect.Method method = SearchActivity.class.getDeclaredMethod("selectSource", SiteSource.class);
            method.setAccessible(true); method.invoke(activity, source);
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private static void loadFixture(ActivityScenario<SearchActivity> scenario) { loadFixture(scenario, SiteSource.VIDER); }
    private static void loadFixture(ActivityScenario<SearchActivity> scenario, SiteSource source) {
        scenario.onActivity(activity -> {
            selectSource(activity, source);
            String url = source.home + "/search/all/local-input-test";
            engine(activity).loadDataWithBaseURL(url, FIXTURE, "text/html", "UTF-8", url);
        });
        AtomicBoolean loaded = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (!loaded.get() && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity(activity -> engine(activity).evaluateJavascript(
                "document.getElementById('code') !== null", value -> loaded.set("true".equals(value))));
            SystemClock.sleep(100);
        }
        assertTrue("Local input fixture did not load", loaded.get());
        // Test the same screen transition as a detected server challenge. Hidden WebView
        // layout is not guaranteed for loadDataWithBaseURL, unlike a real page navigation.
        scenario.onActivity(activity -> {
            try {
                java.lang.reflect.Method method = SearchActivity.class.getDeclaredMethod("showVerification", String.class);
                method.setAccessible(true); method.invoke(activity, "verification");
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }
    private static void awaitKeyboard(ActivityScenario<SearchActivity> scenario, boolean expected) {
        long deadline = SystemClock.elapsedRealtime() + 20000;
        AtomicBoolean seen = new AtomicBoolean(false);
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity(activity -> {
                WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
                seen.set(insets != null && insets.isVisible(WindowInsets.Type.ime()) == expected
                    && field(activity, "verificationPanel") != null);
            });
            if (seen.get()) return;
            SystemClock.sleep(100);
        }
        AtomicReference<String> details = new AtomicReference<>();
        scenario.onActivity(activity -> {
            WebView web = engine(activity);
            details.set("panel=" + (field(activity,"verificationPanel") != null)
                + ", viewFocus=" + web.hasFocus() + ", windowFocus=" + web.hasWindowFocus()
                + ", decorFocus=" + activity.getWindow().getDecorView().hasWindowFocus()
                + ", attached=" + web.isAttachedToWindow()
                + ", nativeCode=" + (field(activity,"verificationCode") != null)
                + ", flags=" + activity.getWindow().getAttributes().flags
                + ", url=" + web.getUrl());
            web.evaluateJavascript("JSON.stringify({active:document.activeElement&&document.activeElement.id,height:innerHeight,inputHeight:document.getElementById('code')&&document.getElementById('code').getBoundingClientRect().height})",
                value -> android.util.Log.i("KeyboardTest", "Fixture editor state: " + value));
            android.util.Log.i("KeyboardTest", ((DiagnosticLog)field(activity,"diagnostics")).export());
        });
        fail("Keyboard visibility did not become " + expected + ": " + details.get());
    }
    private static Button findButton(View root, String text) {
        if (root instanceof Button && text.contentEquals(((Button) root).getText())) return (Button) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), text); if (found != null) return found;
            }
        }
        return null;
    }
    @Test public void verificationInputShowsKeyboardAndReceivesTypedText() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            loadFixture(scenario);
            awaitKeyboard(scenario, true);
            scenario.onActivity(activity -> {
                android.widget.EditText code = (android.widget.EditText) field(activity, "verificationCode");
                assertNotNull(code); assertTrue(code.hasFocus());
            });
            InstrumentationRegistry.getInstrumentation().sendStringSync("keyboardtest");
            scenario.onActivity(activity -> {
                android.widget.EditText code = (android.widget.EditText) field(activity, "verificationCode");
                assertEquals("keyboardtest", code.getText().toString());
                Button insert = findButton(activity.getWindow().getDecorView(), "Wstaw kod");
                assertNotNull(insert); insert.performClick();
            });
            AtomicReference<String> value = new AtomicReference<>();
            scenario.onActivity(activity -> engine(activity).evaluateJavascript(
                "document.getElementById('code').value", value::set));
            long deadline = SystemClock.elapsedRealtime() + 5000;
            while (value.get() == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
            assertEquals("\"keyboardtest\"", value.get());
            scenario.onActivity(activity -> {
                Button cancel = findButton(activity.getWindow().getDecorView(), "Anuluj");
                assertNotNull(cancel); cancel.performClick();
                assertNull(field(activity, "verificationPanel"));
                assertEquals(View.VISIBLE, ((View) field(activity, "searchContent")).getVisibility());
            });
        }
    }
    @Test public void keyboardButtonReopensTheKeyboardWithoutChangingThePage() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            loadFixture(scenario);
            awaitKeyboard(scenario, true);
            scenario.onActivity(activity -> ((InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(((View)field(activity, "verificationCode")).getWindowToken(), 0));
            awaitKeyboard(scenario, false);
            scenario.onActivity(activity -> {
                Button button = findButton(activity.getWindow().getDecorView(), "Klawiatura");
                assertNotNull(button); button.performClick();
            });
            awaitKeyboard(scenario, true);
        }
    }

    @Test public void freediscVerificationShowsKeyboardAndSwitchingSourceRestoresSearch() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            loadFixture(scenario, SiteSource.FREEDISC);
            awaitKeyboard(scenario, true);
            scenario.onActivity(activity -> {
                assertEquals(SiteSource.FREEDISC, field(activity, "selectedSource"));
                android.widget.EditText query = (android.widget.EditText) field(activity, "query");
                query.setText("Ronin");
                selectSource(activity, SiteSource.VIDER);
                assertNull(field(activity, "verificationPanel"));
                assertEquals("Ronin", query.getText().toString());
                assertTrue(query.getHint().toString().contains("Maverick"));
                assertEquals(0, ((org.json.JSONArray)field(activity, "results")).length());
                assertNull(field(activity, "requestedSearch"));
            });
        }
    }
}
