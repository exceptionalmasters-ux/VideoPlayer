package pl.example.viderplayer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Prototype: delegates playback to the site's own player; no stream extraction. */
public final class MainActivity extends Activity {
    private static final String HOME = "https://vider.pl/";
    private WebView web;
    private LinearLayout content;
    private FrameLayout root;
    private EditText address;
    private TextView status;
    private ProgressBar progress;
    private View customView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private int previousUiFlags;
    private boolean loadFailed;
    private final DiagnosticLog diagnostics = new DiagnosticLog();
    private static final int EXPORT_REPORT = 42;
    private final MovieNavigation movieNavigation = new MovieNavigation();
    private final android.os.Handler sessionHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean alive = true;
    private boolean resumed = true;
    private int pageGeneration;
    private String defaultUserAgent;
    private boolean mobileRedirectRetried;


    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.rgb(17, 24, 32));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        // Respect status and gesture bars, including Android 15 edge-to-edge.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
        TextView title = new TextView(this);
        title.setText("VideoPlayer  /  prototyp");
        title.setTextColor(Color.rgb(114, 220, 196));
        title.setTextSize(18);
        title.setPadding(dp(16), dp(14), dp(16), dp(8));
        content.addView(title);
        LinearLayout input = new LinearLayout(this);
        input.setPadding(dp(8), 0, dp(8), 0);
        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Link filmu z Vider lub Freedisc");
        address.setTextSize(14);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.addView(address, new LinearLayout.LayoutParams(0, dp(52), 1));
        button(input, "Otwórz", () -> openInput(address.getText().toString()));
        content.addView(input);
        LinearLayout actions = new LinearLayout(this);
        button(actions, "Wyniki", this::showSearch);
        button(actions, "Odśwież", () -> web.reload());
        button(actions, "★", this::bookmark);
        button(actions, "Lista", this::showBookmarks);
        button(actions, "↗", () -> external(web.getUrl()));
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.addView(actions);
        content.addView(scroll);
        LinearLayout tools = new LinearLayout(this);
        button(tools, "Widok filmu", this::focusPlayer);
        button(tools, "Wróć do filmu", () -> { if (movieNavigation.target() != null) loadPage(movieNavigation.target()); });
        button(tools, "Diagnostyka", this::showDiagnostics);
        android.widget.HorizontalScrollView toolsScroll = new android.widget.HorizontalScrollView(this);
        toolsScroll.addView(tools);
        content.addView(toolsScroll);
        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12);
        status.setPadding(dp(12), 0, dp(12), dp(6));
        status.setText("Wybierz film na stronie lub wklej jego link.");
        content.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        content.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        web = new WebView(this);
        content.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        WebSettings settings = web.getSettings();
        defaultUserAgent = settings.getUserAgentString();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        installDiagnostics();
        diagnostics.add("app.start", HOME, 0);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String path = request.getUrl().getPath();
                if (path != null && (path.contains("/embed/video/") || path.endsWith(".mp4")
                        || path.endsWith(".m3u8") || path.endsWith(".mpd"))) {
                    diagnostics.add("resource.request", request.getUrl().toString(), 0);
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                diagnostics.add(request.isForMainFrame() ? "navigation.main" : "navigation.frame", url, 0);
                if (!request.isForMainFrame()) {
                    // Keep HTTPS embedded players in the page; reject custom schemes.
                    return !"https".equalsIgnoreCase(request.getUrl().getScheme());
                }
                if (SiteSource.isMobileFreediscUrl(url) && SiteSource.fromUrl(movieNavigation.target()) == SiteSource.FREEDISC) {
                    diagnostics.add("navigation.mobile_freedisc", null, 0);
                    if (!mobileRedirectRetried) {
                        mobileRedirectRetried = true;
                        final String target = movieNavigation.target();
                        web.post(() -> loadPage(target));
                    } else status.setText("Freedisc ponownie przekierowuje do wersji mobilnej. Zapisz raport.");
                    return true;
                }
                if (allowed(url)) {
                    SiteSource source = SiteSource.fromUrl(url);
                    if (!source.userAgent(defaultUserAgent).equals(web.getSettings().getUserAgentString())) {
                        loadPage(url); return true;
                    }
                    return false;
                }
                if (request.hasGesture() && "https".equalsIgnoreCase(request.getUrl().getScheme())) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Link poza obsługiwanymi serwisami")
                        .setMessage("Otworzyć w przeglądarce?\n" + request.getUrl().getHost())
                        .setPositiveButton("Otwórz", (d, w) -> external(url))
                        .setNegativeButton("Anuluj", null).show();
                }
                diagnostics.add("navigation.blocked", url, 0);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                loadFailed = false;
                ++pageGeneration;
                diagnostics.add("page.start", url, 0);
                address.setText(url);
                status.setText("Ładowanie…");
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                diagnostics.add("page.finished", url, 0);
                if (!loadFailed) status.setText("Strona załadowana • wybierz film i dotknij Play");
                progress.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                if (UrlPolicy.isHome(url) && movieNavigation.target() != null) {
                    status.setText("Serwis otworzył stronę główną. Zachowuję wybrany film…");
                    returnToMovie(pageGeneration, 0);
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                diagnostics.add("network.error", request.getUrl().toString(), error.getErrorCode());
                if (request.isForMainFrame()) {
                    loadFailed = true;
                    status.setText("Nie udało się otworzyć strony. Sprawdź sieć lub użyj ↗.");
                }
            }
            @Override public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler,
                    android.net.http.SslError error) {
                diagnostics.add("tls.error", error.getUrl(), error.getPrimaryError());
                handler.cancel();
                loadFailed = true;
                status.setText("Błąd certyfikatu TLS. Połączenie zostało przerwane.");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                diagnostics.add("http.error", request.getUrl().toString(), response.getStatusCode());
                if (request.isForMainFrame()) {
                    loadFailed = true;
                    status.setText("Serwer: HTTP " + response.getStatusCode()
                        + ". Sprawdź treść strony; może wymagać weryfikacji.");
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                // Do not store arbitrary console text: it may contain account data.
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    diagnostics.add("javascript.error", message.sourceId(), message.lineNumber());
                }
                return true;
            }
            @Override public void onProgressChanged(WebView view, int value) { progress.setProgress(value); }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, android.os.Message result) {
                diagnostics.add("popup.blocked", null, 0);
                return false;
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                diagnostics.add("fullscreen.enter", web.getUrl(), 0);
                customView = view;
                fullscreenCallback = callback;
                previousUiFlags = getWindow().getDecorView().getSystemUiVisibility();
                content.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            @Override public void onHideCustomView() { exitFullscreen(); }
        });
        address.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || (event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                openInput(address.getText().toString()); return true;
            }
            return false;
        });
        if (state != null) movieNavigation.restore(state.getString("selectedMovie"), state.getBoolean("retryUsed"));
        String shared = sharedUrl(getIntent());
        if (state != null && SiteSource.fromUrl(movieNavigation.target()) != null)
            web.getSettings().setUserAgentString(SiteSource.fromUrl(movieNavigation.target()).userAgent(defaultUserAgent));
        if (state != null && web.restoreState(state) != null) { /* Restore the current session page. */ }
        else if (shared != null) { movieNavigation.select(shared); loadPage(shared); }
        else loadPage(HOME);
    }

    private void loadPage(String url) {
        SiteSource source = SiteSource.fromUrl(url);
        if (source == null) return;
        web.getSettings().setUserAgentString(source.userAgent(defaultUserAgent));
        web.loadUrl(url);
    }
    private boolean allowed(String value) { return UrlPolicy.allowed(value); }

    private String script(String name) {
        try (java.io.InputStream input = getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) { return "''"; }
    }
    private void installDiagnostics() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Set<String> origins = UrlPolicy.mediaOrigins();
            WebViewCompat.addWebMessageListener(web, "vpDiagnostics", origins,
                (view, message, sourceOrigin, mainFrame, reply) -> {
                    if (message.getType() == androidx.webkit.WebMessageCompat.TYPE_STRING) {
                        diagnostics.media(message.getData(), sourceOrigin.toString());
                    }
                });
            WebViewCompat.addDocumentStartJavaScript(web, script("media-diagnostics.js"), origins);
            diagnostics.add("media.observer.enabled", null, 0);
        } else {
            diagnostics.add("media.observer.unsupported", null, 0);
            toast("Zaktualizuj Android System WebView, aby włączyć diagnostykę filmu.");
        }
    }
    private void focusPlayer() {
        if (!allowed(web.getUrl())) return;
        web.evaluateJavascript(script("focus-player.js"), result -> {
            if ("\"missing\"".equals(result)) toast("Nie znaleziono odtwarzacza. Otwórz pełną stronę filmu i uruchom odtwarzanie.");
            else if ("\"player\"".equals(result)) {
                diagnostics.add("focus.enter", web.getUrl(), 0);
                status.setText("Widok odtwarzacza • reklamy wewnątrz ramki pozostają aktywne");
            } else if ("\"page\"".equals(result)) {
                diagnostics.add("focus.exit", web.getUrl(), 0);
                status.setText("Pełna strona");
            }
        });
    }
    private void showSearch() {
        startActivity(new Intent(this, SearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    private void returnToMovie(int generation, int attempt) {
        if (!alive || !resumed || generation != pageGeneration || attempt > 45 || !UrlPolicy.isHome(web.getUrl())) return;
        String check = "(function(){return Array.from(document.querySelectorAll('#qc-cmp2-ui,#onetrust-banner-sdk,.qc-cmp2-consent-info')).some(function(e){return e.getBoundingClientRect().height>0 && getComputedStyle(e).visibility!=='hidden';});}())";
        web.evaluateJavascript(check, result -> {
            if (!alive || !resumed || generation != pageGeneration) return;
            boolean consentVisible = "true".equals(result);
            String target = movieNavigation.retryFrom(web.getUrl(), consentVisible);
            if (target != null) {
                sessionHandler.postDelayed(() -> {
                    if (alive && resumed && generation == pageGeneration && UrlPolicy.isHome(web.getUrl())) {
                        diagnostics.add("movie.return_after_home", target, 0);
                        CookieManager.getInstance().flush();
                        loadPage(target);
                    }
                }, 1200);
            } else if (consentVisible && !movieNavigation.retryUsed()) {
                sessionHandler.postDelayed(() -> returnToMovie(generation, attempt + 1), 2000);
            } else {
                status.setText("Jeśli serwis zatrzymał się na stronie głównej, dotknij „Wróć do filmu”.");
            }
        });
    }
    private void showDiagnostics() {
        new AlertDialog.Builder(this).setTitle("Diagnostyka • " + diagnostics.size() + " zdarzeń")
            .setMessage("Raport przechowujemy w pamięci aplikacji. Nie zawiera cookies, nagłówków, treści strony ani parametrów adresów. Po zamknięciu procesu znika. Eksport zapisuje plik JSON w wybranym miejscu.")
            .setPositiveButton("Eksportuj JSON", (d, w) -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "videoplayer-diagnostyka.json");
                try { startActivityForResult(intent, EXPORT_REPORT); }
                catch (ActivityNotFoundException e) { toast("Brak systemowego wyboru miejsca zapisu."); }
            })
            .setNeutralButton("Wyczyść raport", (d, w) -> diagnostics.clear())
            .setNegativeButton("Zamknij", null).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_REPORT || result != RESULT_OK || data == null || data.getData() == null) return;
        try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (out == null) throw new IOException("No output stream");
            out.write(diagnostics.export().getBytes(StandardCharsets.UTF_8));
            toast("Zapisano raport. Możesz go załączyć do rozmowy.");
        } catch (IOException | SecurityException e) { toast("Nie udało się zapisać raportu."); }
    }
    private void openInput(String text) {
        String value = text.trim();
        if (!value.contains("://")) value = "https://" + value;
        if (!allowed(value)) { toast("Podaj link HTTPS z Vider lub Freedisc."); return; }
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(address.getWindowToken(), 0);
        web.requestFocus();
        movieNavigation.select(value);
        mobileRedirectRetried = false;
        loadPage(value);
    }
    private String sharedUrl(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null
                && allowed(intent.getData().toString())) return intent.getData().toString();
        if (!Intent.ACTION_SEND.equals(intent.getAction())) return null;
        String value = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (value != null && allowed(value.trim())) return value.trim();
        toast("Udostępnij sam link HTTPS z Vider lub Freedisc.");
        return null;
    }
    private void external(String url) {
        if (url == null || !"https".equalsIgnoreCase(Uri.parse(url).getScheme())) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException e) { toast("Brak przeglądarki do otwarcia linku."); }
    }
    private JSONArray bookmarks() {
        try { return new JSONArray(getPreferences(MODE_PRIVATE).getString("bookmarks", "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }
    private void bookmark() {
        String url = web.getUrl();
        if (!allowed(url)) { toast("Najpierw otwórz stronę filmu."); return; }
        JSONArray list = bookmarks();
        for (int i = 0; i < list.length(); i++) {
            if (list.optJSONObject(i) != null && url.equals(list.optJSONObject(i).optString("url"))) { toast("Ten link jest już na liście."); return; }
        }
        try {
            String title = web.getTitle();
            list.put(new JSONObject().put("url", url).put("title", title == null ? url : title));
            getPreferences(MODE_PRIVATE).edit().putString("bookmarks", list.toString()).apply();
            toast("Dodano do ulubionych.");
        } catch (JSONException e) { toast("Nie udało się zapisać linku."); }
    }
    private void showBookmarks() {
        JSONArray list = bookmarks();
        if (list.length() == 0) { toast("Lista jest pusta. Dodaj stronę przyciskiem ★."); return; }
        String[] names = new String[list.length()];
        for (int i = 0; i < names.length; i++) names[i] = list.optJSONObject(i).optString("title");
        new AlertDialog.Builder(this).setTitle("Ulubione")
            .setItems(names, (d, which) -> openInput(list.optJSONObject(which).optString("url")))
            .setNeutralButton("Wyczyść listę", (d, w) -> getPreferences(MODE_PRIVATE).edit().remove("bookmarks").apply())
            .setNegativeButton("Zamknij", null).show();
    }
    private void exitFullscreen() {
        if (customView == null) return;
        diagnostics.add("fullscreen.exit", web.getUrl(), 0);
        root.removeView(customView);
        customView = null;
        content.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(previousUiFlags);
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenCallback = null;
        if (callback != null) callback.onCustomViewHidden();
    }
    @Override public void onBackPressed() {
        if (customView != null) exitFullscreen();
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("selectedMovie", movieNavigation.target());
        out.putBoolean("retryUsed", movieNavigation.retryUsed());
        web.saveState(out); super.onSaveInstanceState(out);
    }
    @Override protected void onPause() {
        resumed = false; sessionHandler.removeCallbacksAndMessages(null);
        web.onPause(); CookieManager.getInstance().flush(); super.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (web != null) {
            web.onResume();
            if (UrlPolicy.isHome(web.getUrl()) && movieNavigation.target() != null) returnToMovie(pageGeneration, 0);
        }
    }
    @Override protected void onDestroy() {
        alive = false;
        sessionHandler.removeCallbacksAndMessages(null);
        exitFullscreen();
        content.removeView(web);
        web.stopLoading();
        web.destroy();
        super.onDestroy();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private void button(LinearLayout parent, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setOnClickListener(v -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-2, dp(48)));
    }
}
