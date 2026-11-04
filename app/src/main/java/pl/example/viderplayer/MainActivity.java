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
import org.json.JSONTokener;

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
    private LinearLayout codeRow;
    private EditText codeInput;
    private boolean television;
    private boolean playerFullscreen;
    private Button tvControlsAnchor;
    private TvPointer pointer;
    private boolean movieFocused;
    private boolean accessRequired;
    private LinearLayout tvControls;
    private void wirePlayerNavigation(){
        if(!television||web==null||tvControls==null)return;
        java.util.List<View> toolbar=new java.util.ArrayList<>(),code=new java.util.ArrayList<>();
        for(int i=0;i<tvControls.getChildCount();i++)toolbar.add(tvControls.getChildAt(i));
        for(int i=0;i<codeRow.getChildCount();i++)code.add(codeRow.getChildAt(i));
        java.util.List<java.util.List<View>> rows=TvFocus.connect(java.util.Arrays.asList(toolbar,code));
        if(codeRow.getVisibility()!=View.VISIBLE)for(View v:toolbar)v.setNextFocusDownId(web.getId());
        else if(codeRow.getChildCount()>1)codeInput.setNextFocusDownId(codeRow.getChildAt(codeRow.getChildCount()-1).getId());
    }
    private final java.util.Map<View,Integer> playerChrome=new java.util.IdentityHashMap<>();
    private final PresentationPolicy presentation=new PresentationPolicy();
    private int presentationScan;
    private LocalLibrary library;
    private String launchMovieKey="",launchMovieTitle="",launchMovieThumbnail="";
    private final java.util.Map<String,String> playbackFrames=new java.util.HashMap<>();
    private String pendingTvTap="";
    private long pendingTvTapUntil;
    private int pendingTvTapGeneration;
    private int playerUiFlags;
    private Button fullscreenControls;


    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        library=new LocalLibrary(this);
        String launchUrl=sharedUrl(getIntent());
        if(launchUrl!=null&&state==null&&getIntent().getBooleanExtra("fromStart",false)){MovieLibrary.Item saved=library.find(launchUrl);if(saved!=null)library.progress(launchUrl,0,saved.duration,false);}
        if(launchUrl!=null){launchMovieKey=MovieLibrary.key(launchUrl);launchMovieTitle=getIntent().getStringExtra("movieTitle");launchMovieThumbnail=getIntent().getStringExtra("movieThumbnail");}
        television=TvUi.enabled(this);
        TvUi.immersive(this);
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
        title.setText(television?"VideoPlayer TV · pilot: OK / strzałki / Wstecz":"VideoPlayer  /  prototyp");
        title.setTextColor(Color.rgb(114, 220, 196));
        title.setTextSize(18);
        title.setPadding(dp(television?32:16), dp(television?4:14), dp(16), dp(television?2:8));
        content.addView(title);
        LinearLayout input = new LinearLayout(this);
        input.setPadding(dp(8), 0, dp(8), 0);
        address = new EditText(this);
        address.setSingleLine(true);
        TvUi.editor(address);
        address.setHint("Link filmu z Vider lub Freedisc");
        address.setTextSize(14);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.addView(address, new LinearLayout.LayoutParams(0, dp(52), 1));
        button(input, "Otwórz", () -> openInput(address.getText().toString()));
        content.addView(input);
        if(television)input.setVisibility(View.GONE);
        LinearLayout actions = new LinearLayout(this);
        button(actions, "Wyniki", this::showSearch);
        button(actions, "Odśwież", () -> web.reload());
        button(actions, "★", this::bookmark);
        button(actions, "Ulubione", this::showBookmarks);
        button(actions,"Historia",()->showCollection("history"));
        button(actions, "↗", () -> external(web.getUrl()));
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.addView(actions);
        content.addView(scroll);
        if(television){
            scroll.setVisibility(View.GONE);
            tvControls=new LinearLayout(this);tvControls.setPadding(dp(24),0,dp(24),0);
            button(tvControls,"Wyniki",this::showSearch);
            button(tvControls,"★",this::bookmark);
            button(tvControls,"Widok filmu",this::enterTvFullscreen);
            tvControlsAnchor=(Button)tvControls.getChildAt(tvControls.getChildCount()-1);
            button(tvControls,"Strona",this::showOriginalPage);
            button(tvControls,"Kursor",this::togglePointer);
            button(tvControls,"Opcje",this::tvOptions);
            content.addView(tvControls);
        }
        LinearLayout tools = new LinearLayout(this);
        button(tools, "Widok filmu", this::focusPlayer);
        if(!television)button(tools,"Pełny ekran",this::enterTvFullscreen);
        button(tools, "Wróć do filmu", () -> { if (movieNavigation.target() != null) loadPage(movieNavigation.target()); });
        button(tools, "Diagnostyka", this::showDiagnostics);
        if(television){button(tools,"★",this::bookmark);button(tools,"Ulubione",this::showBookmarks);button(tools,"Historia",()->showCollection("history"));}
        android.widget.HorizontalScrollView toolsScroll = new android.widget.HorizontalScrollView(this);
        toolsScroll.addView(tools);
        content.addView(toolsScroll);
        if(television)toolsScroll.setVisibility(View.GONE);
        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12);
        status.setPadding(dp(12), 0, dp(12), dp(6));
        status.setText("Wybierz film na stronie lub wklej jego link.");
        content.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        content.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        codeRow = new LinearLayout(this);
        codeRow.setVisibility(View.GONE);
        codeInput = new EditText(this);
        codeInput.setHint("Kod z obrazka — wpisz tutaj");
        codeInput.setSingleLine(true);
        TvUi.editor(codeInput,()->insertVerificationCode(true));
        codeInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        codeInput.setOnEditorActionListener((view,action,event)->{if(television&&(action==EditorInfo.IME_ACTION_DONE||action==EditorInfo.IME_ACTION_GO)){insertVerificationCode(true);return true;}return false;});
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        codeRow.addView(codeInput, new LinearLayout.LayoutParams(0, dp(56), 1));
        if(!television)button(codeRow,"Wstaw kod",this::insertVerificationCode);
        if(television)button(codeRow,"Zatwierdź",()->insertVerificationCode(true));
        content.addView(codeRow);
        web = new TvWebView(this);
        content.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        fullscreenControls=new Button(this);
        fullscreenControls.setText("☰");
        fullscreenControls.setTextSize(20);
        fullscreenControls.setContentDescription("Pokaż przyciski odtwarzacza");
        fullscreenControls.setBackgroundColor(Color.argb(170,17,24,32));
        fullscreenControls.setTextColor(Color.WHITE);
        fullscreenControls.setVisibility(View.GONE);
        fullscreenControls.setOnClickListener(v->exitPlayerFullscreen());
        FrameLayout.LayoutParams controlsPosition=new FrameLayout.LayoutParams(dp(56),dp(48),android.view.Gravity.TOP|android.view.Gravity.RIGHT);
        controlsPosition.setMargins(dp(12),dp(12),dp(12),dp(12));
        root.addView(fullscreenControls,controlsPosition);
        if(television){
            web.setId(View.generateViewId());
            pointer=new TvPointer(root,web,()->{if(codeRow.getVisibility()==View.VISIBLE)codeInput.requestFocus();else if(tvControlsAnchor!=null)tvControlsAnchor.requestFocus();});
            wirePlayerNavigation();
        }
        WebSettings settings = web.getSettings();
        defaultUserAgent = settings.getUserAgentString();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(!television);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
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
                loadFailed = false;movieFocused=false;accessRequired=false;if(pointer!=null)pointer.stop();
                if(UrlPolicy.isMovie(url))library.opened(url,MovieLibrary.key(url).equals(launchMovieKey)?launchMovieTitle:"",MovieLibrary.key(url).equals(launchMovieKey)?launchMovieThumbnail:"");
                if(playerFullscreen)exitPlayerFullscreen(false);
                presentation.navigation();
                ++presentationScan;
                web.getSettings().setUseWideViewPort(true);
                web.getSettings().setLoadWithOverviewMode(true);
                ++pageGeneration;playbackFrames.clear();pendingTvTap="";
                setCodeVisible(false);
                diagnostics.add("page.start", url, 0);
                address.setText(url);
                status.setText("Ładowanie…");
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                diagnostics.add("page.finished", url, 0);
                updateMovieMetadata(url);
                if (!loadFailed) status.setText("Strona załadowana • wybierz film i dotknij Play");
                progress.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                if(television){web.evaluateJavascript(script("tv-play-tap.js")+";"+script("tv-media.js"),null);if(tvControlsAnchor!=null&&codeRow.getVisibility()!=View.VISIBLE)tvControlsAnchor.requestFocus();}
                inspectVerification(pageGeneration, 0);
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
                presentation.userControlled();
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
        if(television&&WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)){
            if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){
                WebViewCompat.addWebMessageListener(web,"vpTvTouch",UrlPolicy.mediaOrigins(),(view,message,origin,mainFrame,reply)->{
                    if(!mainFrame||!alive||!resumed||pendingTvTap.isEmpty()||pendingTvTapGeneration!=pageGeneration
                        ||android.os.SystemClock.uptimeMillis()>pendingTvTapUntil||!web.isShown()||customView!=null
                        ||codeRow.getVisibility()==View.VISIBLE||pointer!=null&&pointer.active()
                        ||!UrlPolicy.playback(web.getUrl(),movieNavigation.target())
                        ||message.getType()!=androidx.webkit.WebMessageCompat.TYPE_STRING)return;
                    try{
                        android.net.Uri page=android.net.Uri.parse(web.getUrl());
                        if(!origin.toString().equals(page.getScheme()+"://"+page.getAuthority()))return;
                        String raw=message.getData();if(raw==null||raw.length()>512)return;
                        JSONObject tap=new JSONObject(raw);if(!pendingTvTap.equals(tap.optString("id")))return;
                        double nx=tap.optDouble("x",-1),ny=tap.optDouble("y",-1);
                        if(!Double.isFinite(nx)||!Double.isFinite(ny)||nx<0||nx>1||ny<0||ny>1)return;
                        pendingTvTap="";
                        float x=(float)(nx*web.getWidth()),y=(float)(ny*web.getHeight());
                        long now=android.os.SystemClock.uptimeMillis();
                        android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,y,0);
                        android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+50,android.view.MotionEvent.ACTION_UP,x,y,0);
                        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                        web.dispatchTouchEvent(down);web.dispatchTouchEvent(up);down.recycle();up.recycle();
                        diagnostics.add("tv.play_control_tapped",null,0);
                    }catch(Exception ignored){}
                });
            }
            WebViewCompat.addDocumentStartJavaScript(web,script("tv-play-tap.js")+";"+script("tv-media.js"),UrlPolicy.mediaOrigins());
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, script("frame-fit.js"), UrlPolicy.mediaOrigins());
        }
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
            WebViewCompat.addWebMessageListener(web,"vpPlayback",origins,(view,message,origin,mainFrame,reply)->{
                if(!alive||message.getType()!=androidx.webkit.WebMessageCompat.TYPE_STRING)return;
                try{
                    String raw=message.getData();if(raw==null||raw.length()>4096)return;
                    JSONObject data=new JSONObject(raw);
                    String movie=UrlPolicy.isMovie(web.getUrl())?web.getUrl():movieNavigation.target();
                    if(!UrlPolicy.playback(web.getUrl(),movie)||!UrlPolicy.isMovie(movie))return;
                    String frame=data.optString("page");
                    if(!UrlPolicy.playback(frame,movie)||!android.net.Uri.parse(frame).getScheme().equals("https")||!origin.toString().equals(android.net.Uri.parse(frame).getScheme()+"://"+android.net.Uri.parse(frame).getAuthority()))return;
                    if(UrlPolicy.isMovie(frame)&&!MovieLibrary.key(frame).equals(MovieLibrary.key(movie)))return;
                    if(SiteSource.fromUrl(movie)==SiteSource.VIDER&&frame.contains("/embed/video/")&&!android.net.Uri.parse(frame).getPath().replaceAll("/$", "").endsWith("/"+MovieLibrary.key(movie).substring("vider:f".length())))return;
                    if("hello".equals(data.optString("event"))){
                        if(playbackFrames.size()>32)return;
                        String token=java.util.UUID.randomUUID().toString();playbackFrames.put(token,MovieLibrary.key(movie));
                        MovieLibrary.Item saved=library.find(movie);
                        JSONObject response=new JSONObject().put("token",token).put("position",saved==null?0:saved.position).put("duration",saved==null?0:saved.duration);
                        reply.postMessage(response.toString());
                    }else if(MovieLibrary.key(movie).equals(playbackFrames.get(data.optString("token")))){
                        library.progress(movie,data.optLong("position",-1),data.optLong("duration",-1),data.optBoolean("ended"));
                    }
                }catch(Exception ignored){}
            });
            WebViewCompat.addDocumentStartJavaScript(web,script("playback-progress.js"),origins);
            diagnostics.add("media.observer.enabled", null, 0);
        } else {
            diagnostics.add("media.observer.unsupported", null, 0);
            toast("Zaktualizuj Android System WebView, aby włączyć diagnostykę filmu.");
        }
    }
    private void inspectVerification(int generation, int attempt) {
        inspectPresentation(generation,++presentationScan,0);
    }
    private boolean currentPresentation(int generation,int scan){
        return alive&&resumed&&generation==pageGeneration&&scan==presentationScan&&allowed(web.getUrl());
    }
    private void inspectPresentation(int generation,int scan,int attempt){
        if(!currentPresentation(generation,scan))return;
        web.evaluateJavascript(script("search-state.js"),value->{
            if(!currentPresentation(generation,scan))return;
            String stage="results";
            try{stage=new JSONObject((String)new JSONTokener(value).nextValue()).optString("stage","results");}
            catch(Exception ignored){ }
            final boolean blocked=!"results".equals(stage);accessRequired=blocked;
            if(blocked){
                presentation.accessBlocked();
                if(playerFullscreen)exitPlayerFullscreen(false);
                // Use a complete expression rather than toggling an already restored page.
                String restore="(function(){if(!document.getElementById('vp-focus-style'))return 'page';return "+script("focus-player.js")+"}())";
                web.evaluateJavascript(restore,restored->{
                    if(!currentPresentation(generation,scan))return;
                    web.evaluateJavascript(script("verification-layout.js"),visible->{
                        if(!currentPresentation(generation,scan))return;
                        setCodeVisible("true".equals(visible));
                        schedulePresentation(generation,scan,attempt,true);
                    });
                });
            }else{
                web.evaluateJavascript(script("verification-layout.js"),visible->{
                    if(!currentPresentation(generation,scan))return;
                    setCodeVisible("true".equals(visible));
                    if(presentation.shouldEnter()&&UrlPolicy.playback(web.getUrl(),movieNavigation.target()))
                        enterPlayerFullscreen(generation,true);
                    else if(playerFullscreen)web.evaluateJavascript("window.__vpEnsurePlayerFocus ? window.__vpEnsurePlayerFocus() : 'missing'",null);
                    schedulePresentation(generation,scan,attempt,false);
                });
            }
        });
    }
    private void schedulePresentation(int generation,int scan,int attempt,boolean blocked){
        if(!currentPresentation(generation,scan))return;
        if(attempt==60&&presentation.shouldEnter()&&UrlPolicy.playback(web.getUrl(),movieNavigation.target())&&!blocked){
            diagnostics.add("presentation.player_not_ready",web.getUrl(),0);
            status.setText("Czekam na odtwarzacz. Widok filmu włączy się, gdy będzie gotowy.");
        }
        if(blocked||playerFullscreen||presentation.shouldEnter()||attempt<10){
            int nextAttempt=blocked?0:attempt+1;
            sessionHandler.postDelayed(()->inspectPresentation(generation,scan,nextAttempt),attempt<60?1500:3000);
        }
    }
    private void setCodeVisible(boolean visible) {
        if(television)((TvWebView)web).codePage(visible,this::submitPageCode);
        if (visible == (codeRow.getVisibility() == View.VISIBLE)) return;
        codeRow.setVisibility(visible ? View.VISIBLE : View.GONE);
        web.getSettings().setUseWideViewPort(!visible);
        web.getSettings().setLoadWithOverviewMode(!visible);
        wirePlayerNavigation();
        if (visible) {
            movieFocused=false;
            diagnostics.add("verification.open", web.getUrl(), 0);
            if(pointer!=null&&pointer.active())return;
            if(television)codeInput.setShowSoftInputOnFocus(true);
            codeInput.requestFocus();
            codeInput.post(() -> {
                if (alive && codeRow.getVisibility() == View.VISIBLE)
                    ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(codeInput,InputMethodManager.SHOW_IMPLICIT);
            });
        } else {
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(codeInput.getWindowToken(),0);
            codeInput.setText("");
            if(television&&tvControlsAnchor!=null)tvControlsAnchor.requestFocus();
        }
    }
    private void insertVerificationCode() { insertVerificationCode(false); }
    private void insertVerificationCode(boolean submit) {
        if (!allowed(web.getUrl()) || codeRow.getVisibility() != View.VISIBLE) return;
        String code = codeInput.getText().toString();
        if (code.isEmpty()) { codeInput.setError("Wpisz kod z obrazka"); return; }
        web.evaluateJavascript(script("verification-fill.js").replace("__VP_MANUAL_CODE__",JSONObject.quote(code)), value -> {
            if ("true".equals(value)) {
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(codeInput.getWindowToken(),0);
                if(submit)submitPageCode();
                else toast("Kod wstawiony. Użyj przycisku na stronie, aby go zatwierdzić.");
            } else toast("Nie znaleziono pola kodu na stronie.");
        });
    }
    private void submitPageCode(){
        if(!allowed(web.getUrl())||codeRow.getVisibility()!=View.VISIBLE)return;
        web.evaluateJavascript(script("verification-submit.js"),sent->{
            if(!alive)return;
            if("true".equals(sent)){
                if(pointer!=null)pointer.stop();
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(web.getWindowToken(),0);
                presentation.navigation();inspectVerification(pageGeneration,0);
                diagnostics.add("verification.page_confirmed",web.getUrl(),0);
            }else toast("Nie znaleziono wpisanego kodu i przycisku Wchodzę na stronie.");
        });
    }
    private void tvCommand(String action) {
        if(!television||!allowed(web.getUrl()))return;
        if(codeRow.getVisibility()==View.VISIBLE){toast("Najpierw dokończ weryfikację kodu.");return;}
        diagnostics.add("tv.command."+action,null,0);
        pendingTvTap=("toggle".equals(action)||"play".equals(action))?java.util.UUID.randomUUID().toString():"";
        pendingTvTapUntil=android.os.SystemClock.uptimeMillis()+2000;pendingTvTapGeneration=pageGeneration;
        String command="(function(){return !!(window.__vpTvCommand && window.__vpTvCommand("+JSONObject.quote(action)+","+JSONObject.quote(pendingTvTap)+"));}())";
        web.evaluateJavascript(command,value->{if(!"true".equals(value))toast("Nie znaleziono gotowego odtwarzacza. Wybierz Strona i uruchom film.");});
    }
    private void enterTvFullscreen() {
        enterPlayerFullscreen(pageGeneration,false);
    }
    private void enterPlayerFullscreen(int generation,boolean automatic){
        if(!alive||!resumed||generation!=pageGeneration||!allowed(web.getUrl())||playerFullscreen)return;
        if(automatic&&!presentation.shouldEnter())return;
        if(codeRow.getVisibility()==View.VISIBLE){if(!automatic)toast("Najpierw dokończ weryfikację kodu.");return;}
        String focus="window.__vpEnsurePlayerFocus ? window.__vpEnsurePlayerFocus() : "+script("focus-player.js");
        web.evaluateJavascript(focus,value->{
            if(!alive||!resumed||generation!=pageGeneration||playerFullscreen||automatic&&!presentation.shouldEnter())return;
            if(!"\"player\"".equals(value)){if(!automatic)toast("Najpierw uruchom film na stronie.");return;}
            web.getSettings().setUseWideViewPort(false);web.getSettings().setLoadWithOverviewMode(false);
            if(automatic)presentation.automaticallyEntered();else presentation.userControlled();
            status.setText("Widok filmu");
            movieFocused=true;playerFullscreen=true;playerChrome.clear();
            playerUiFlags=getWindow().getDecorView().getSystemUiVisibility();
            for(int i=0;i<content.getChildCount();i++){
                View child=content.getChildAt(i);
                if(child!=web){playerChrome.put(child,child.getVisibility());child.setVisibility(View.GONE);}
            }
            fullscreenControls.setVisibility(television?View.GONE:View.VISIBLE);
            web.requestFocus();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            diagnostics.add(automatic?"presentation.auto_fullscreen":"presentation.fullscreen",web.getUrl(),0);
            if(!automatic&&television)toast("OK: pauza/wznów · lewo/prawo: 10 sekund · Wstecz: przyciski");
        });
    }
    private void exitPlayerFullscreen(){exitPlayerFullscreen(true);}
    private void exitPlayerFullscreen(boolean user){
        if(user)presentation.userControlled();
        if(!playerFullscreen)return;
        playerFullscreen=false;
        for(java.util.Map.Entry<View,Integer> entry:playerChrome.entrySet())entry.getKey().setVisibility(entry.getValue());
        playerChrome.clear();
        fullscreenControls.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(playerUiFlags);
        diagnostics.add("presentation.controls",web.getUrl(),user?1:0);
        if(tvControlsAnchor!=null)tvControlsAnchor.requestFocus();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(!television)return super.dispatchKeyEvent(event);
        if(pointer!=null&&pointer.key(event))return true;
        if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)togglePointer();return true;}
        String command=TvKeys.media(event.getKeyCode());
        if(command!=null){if(event.getAction()==KeyEvent.ACTION_UP)tvCommand(command);return true;}
        if(playerFullscreen||customView!=null){
            command=TvKeys.fullscreen(event.getKeyCode());
            if(command!=null){
                if(event.getAction()==KeyEvent.ACTION_UP){
                    if(command.equals("controls")){exitPlayerFullscreen();exitFullscreen();}
                    else tvCommand(command);
                }return true;
            }
        } else if(web!=null&&web.hasFocus()){
            int key=event.getKeyCode();
            if(movieFocused&&codeRow.getVisibility()!=View.VISIBLE){
                command=TvKeys.fullscreen(key);
                if(command!=null){if(event.getAction()==KeyEvent.ACTION_UP){if(command.equals("controls")){if(tvControlsAnchor!=null)tvControlsAnchor.requestFocus();}else tvCommand(command);}return true;}
            }
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){
                if(event.getAction()==KeyEvent.ACTION_UP){
                    int meta=(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_LEFT)?KeyEvent.META_SHIFT_ON:0;
                    web.dispatchKeyEvent(new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB,0,meta));
                    web.dispatchKeyEvent(new KeyEvent(0,0,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TAB,0,meta));
                }return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private void focusPlayer() {
        if (!allowed(web.getUrl())) return;
        if (codeRow.getVisibility() == View.VISIBLE) { toast("Najpierw dokończ weryfikację kodu."); return; }
        presentation.userControlled();
        web.evaluateJavascript(script("focus-player.js"), result -> {
            if ("\"missing\"".equals(result)) toast("Nie znaleziono odtwarzacza. Otwórz pełną stronę filmu i uruchom odtwarzanie.");
            else if ("\"player\"".equals(result)) {
                web.getSettings().setUseWideViewPort(false);
                web.getSettings().setLoadWithOverviewMode(false);
                diagnostics.add("focus.enter", web.getUrl(), 0);
                movieFocused=true;
                status.setText("Widok odtwarzacza • reklamy wewnątrz ramki pozostają aktywne");
            } else if ("\"page\"".equals(result)) {
                web.getSettings().setUseWideViewPort(true);
                web.getSettings().setLoadWithOverviewMode(true);
                movieFocused=false;
                diagnostics.add("focus.exit", web.getUrl(), 0);
                status.setText("Pełna strona");
            }
        });
    }
    private void togglePointer(){
        if(pointer==null)return;
        if(customView!=null)exitFullscreen();
        boolean on=pointer.toggle();toast(on?"Kursor: strzałki przesuwają · OK klika · Wstecz wyłącza":"Kursor wyłączony");
    }
    private void showOriginalPage(){
        boolean temporary=accessRequired||codeRow.getVisibility()==View.VISIBLE||presentation.shouldEnter()&&!movieFocused;
        if(pointer!=null)pointer.stop();exitFullscreen();exitPlayerFullscreen(false);if(!temporary)presentation.userControlled();movieFocused=false;
        web.evaluateJavascript("(function(){if(!document.getElementById('vp-focus-style'))return 'page';return "+script("focus-player.js")+"}())",value->{
            web.getSettings().setUseWideViewPort(true);web.getSettings().setLoadWithOverviewMode(true);web.requestFocus();
            status.setText("Pełna strona · Menu włącza kursor pilota");
        });
    }
    private void tvOptions(){
        new AlertDialog.Builder(this).setTitle("Opcje").setItems(new String[]{"Ulubione","Historia","Odśwież","Wróć do filmu","Diagnostyka"},(d,which)->{
            if(which==0)showBookmarks();else if(which==1)showCollection("history");else if(which==2)web.reload();else if(which==3){if(movieNavigation.target()!=null)loadPage(movieNavigation.target());}else showDiagnostics();
        }).setNegativeButton("Zamknij",null).show();
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
    private void updateMovieMetadata(String url){
        if(!UrlPolicy.isMovie(url)||!MovieLibrary.key(url).equals(MovieLibrary.key(web.getUrl())))return;
        final int generation=pageGeneration;
        web.evaluateJavascript("(function(){const h=document.querySelector('h1');const m=document.querySelector('meta[property=\"og:image\"],link[rel=\"image_src\"]');return JSON.stringify({title:h?h.textContent:document.title,thumbnail:m?(m.content||m.href||''):''});}())",value->{
            if(!alive||generation!=pageGeneration)return;
            try{JSONObject item=new JSONObject((String)new JSONTokener(value).nextValue());library.metadata(url,item.optString("title"),item.optString("thumbnail"));}catch(Exception ignored){}
        });
    }
    private void bookmark() {
        String url=web.getUrl();
        if(!UrlPolicy.isMovie(url))url=movieNavigation.target();
        if(!UrlPolicy.isMovie(url)){toast("Najpierw otwórz film.");return;}
        MovieLibrary.Item item=library.find(url);
        boolean added=library.toggle(url,item==null?"":item.title,item==null?"":item.thumbnail);
        toast(added?"Dodano do ulubionych.":"Usunięto z ulubionych.");
    }
    private void showBookmarks(){showCollection("favorites");}
    private void showCollection(String collection){
        startActivity(new Intent(this,SearchActivity.class).putExtra("collection",collection).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
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
        if(pointer!=null&&pointer.active()){pointer.stop();return;}
        if(customView!=null){exitFullscreen();if(playerFullscreen)exitPlayerFullscreen();else presentation.userControlled();}
        else if(playerFullscreen)exitPlayerFullscreen();
        else if(television)finish();
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("selectedMovie", movieNavigation.target());
        out.putBoolean("retryUsed", movieNavigation.retryUsed());
        web.saveState(out); super.onSaveInstanceState(out);
    }
    @Override protected void onPause() {
        resumed = false;pendingTvTap=""; if(pointer!=null)pointer.stop(); sessionHandler.removeCallbacksAndMessages(null);
        web.evaluateJavascript("window.__vpFlushProgress && window.__vpFlushProgress()",null);
        web.onPause(); CookieManager.getInstance().flush(); super.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        TvUi.immersive(this);
        if (web != null) {
            web.onResume();
            inspectVerification(pageGeneration, 0);
            if (UrlPolicy.isHome(web.getUrl()) && movieNavigation.target() != null) returnToMovie(pageGeneration, 0);
        }
    }
    @Override protected void onDestroy() {
        alive = false;
        sessionHandler.removeCallbacksAndMessages(null);
        exitFullscreen();
        if(playerFullscreen)exitPlayerFullscreen(false);
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
        button.setTextSize(television?16:12);
        TvUi.focusable(button);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dp(48));
        if(television)params.setMargins(dp(4),dp(4),dp(4),dp(4));
        parent.addView(button,params);
    }
}

