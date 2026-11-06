package pl.example.viderplayer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.nio.charset.StandardCharsets;

/** Native-only results UI; the site search runs in an invisible WebView. */
public final class SearchActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ThumbnailLoader thumbnails = new ThumbnailLoader();
    private EditText query;
    private TextView status;
    private GridLayout grid;
    private ScrollView resultsView;
    private WebView engine;
    private JSONArray results = new JSONArray();
    private JSONArray searchResults = new JSONArray();
    private String collection = "results";
    private LocalLibrary library;
    private Button clearCollection;
    private int scanToken;
    private boolean active = true;
    private final DiagnosticLog diagnostics = new DiagnosticLog();
    private static final int EXPORT_REPORT = 43;
    private FrameLayout engineHost;
    private FrameLayout screen;
    private LinearLayout searchContent;
    private LinearLayout verificationPanel;
    private EditText verificationCode;
    private String requestedSearch;
    private boolean searchReturnUsed;
    private long lastSearchAt;
    private int lastHttpError;
    private Spinner sourcePicker;
    private Button previewButton;
    private String verificationStage;
    private boolean searchRunning;
    private boolean navigationStarted;
    private int requestGeneration;
    private Runnable searchTimeout;
    private static final long SEARCH_TIMEOUT_MS = 30000;
    private String defaultUserAgent;
    private boolean mobileRedirectRetried;
    private boolean freediscRouted;
    private int freediscRouteAttempts;
    private boolean television;
    private int selectedResultIndex;
    private Button searchButton;
    private TvPointer pointer;
    private final java.util.List<java.util.List<View>> tvRows=new java.util.ArrayList<>();
    private java.util.List<View> tvLastRow=new java.util.ArrayList<>();
    private SiteSource selectedSource = SiteSource.VIDER;
    private GoogleFallback google;
    private String requestedTitle;
    private boolean fallbackUsed,forceGoogle;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        library = new LocalLibrary(this);
        television=TvUi.enabled(this);
        TvUi.immersive(this);
        if(saved!=null)selectedResultIndex=saved.getInt("selectedResultIndex",0);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 24, 32));
        if(television)root.setPadding(dp(32),dp(10),dp(32),dp(10));
        searchContent = root;
        screen = new FrameLayout(this);
        screen.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        screen.requestApplyInsets();
        TextView brand = text(television?"VideoPlayer TV":"VideoPlayer", television?22:26, Color.WHITE);
        brand.setTypeface(null, Typeface.BOLD);
        brand.setPadding(dp(16), dp(television?2:18), dp(16), dp(2));
        root.addView(brand);
        TextView caption = text(television?"Strzałki: wybór · OK: otwórz · Wstecz: powrót":"Znajdź film i dotknij miniatury", 14, Color.LTGRAY);
        caption.setPadding(dp(16), 0, dp(16), dp(television?4:14));
        root.addView(caption);
        selectedSource = SiteSource.restore(saved != null ? saved.getString("source")
            : getSharedPreferences("sources", MODE_PRIVATE).getString("selected", SiteSource.VIDER.name()));
        if(saved==null&&getIntent().hasExtra("searchSource"))selectedSource=SiteSource.restore(getIntent().getStringExtra("searchSource"));
        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setPadding(dp(16), 0, dp(16), 0);
        sourceRow.setGravity(Gravity.CENTER_VERTICAL);
        sourceRow.addView(text("Źródło: ", 14, Color.LTGRAY));
        sourcePicker = new Spinner(this);
        sourcePicker.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{SiteSource.VIDER.label, SiteSource.FREEDISC.label}){
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
                View v=super.getView(position,recycled,parent);if(television&&v instanceof TextView){((TextView)v).setTextColor(Color.WHITE);((TextView)v).setTextSize(18);}return v;
            }
        });
        sourcePicker.setSelection(selectedSource.ordinal());
        TvUi.focusable(sourcePicker);
        sourceRow.addView(sourcePicker, new LinearLayout.LayoutParams(0, dp(television?40:48), 1));
        root.addView(sourceRow);
        LinearLayout input = new LinearLayout(this);
        input.setPadding(dp(12), 0, dp(12), 0);
        query = new TvEditText(this);
        query.setSingleLine(true);
        TvUi.editor(query,this::search);
        query.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        query.setHint("Tytuł filmu, np. " + selectedSource.example);
        query.setTextSize(television?20:16);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.addView(query, new LinearLayout.LayoutParams(0, dp(52), 1));
        addButton(input, "Szukaj", this::search);
        searchButton=(Button)input.getChildAt(input.getChildCount()-1);
        root.addView(input);
        LinearLayout actions = new LinearLayout(this);
        addButton(actions, "Mam link", this::pasteLink);
        addButton(actions,"Google",()->{forceGoogle=true;lastSearchAt=0;search();forceGoogle=false;});
        addButton(actions, "Raport", this::exportReport);
        root.addView(actions);
        LinearLayout collections = new LinearLayout(this);
        addButton(collections,"Wyniki",()->showCollection("results"));
        addButton(collections,"Historia",()->showCollection("history"));
        addButton(collections,"Ulubione",()->showCollection("favorites"));
        addButton(collections,"Wyczyść",this::clearCollection);
        clearCollection=(Button)collections.getChildAt(collections.getChildCount()-1);
        clearCollection.setVisibility(View.GONE);
        HorizontalScrollView collectionsScroll=new HorizontalScrollView(this);
        collectionsScroll.addView(collections);root.addView(collectionsScroll);
        previewButton = new Button(this);
        previewButton.setText("Pokaż stronę");
        TvUi.focusable(previewButton);
        previewButton.setVisibility(View.GONE);
        previewButton.setOnClickListener(v -> previewSearch());
        root.addView(previewButton);
        status = text("Wpisz tytuł. Filmy pokażą się tutaj jako miniatury.", 13, Color.LTGRAY);
        status.setPadding(dp(16), dp(8), dp(16), dp(10));
        root.addView(status);
        FrameLayout workspace = new FrameLayout(this);
        root.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1));
        engine = new TvWebView(this);
        engineHost = new FrameLayout(this);
        workspace.addView(engineHost, new FrameLayout.LayoutParams(-1, -1));
        engineHost.addView(engine, new FrameLayout.LayoutParams(-1, -1));
        if(television)pointer=new TvPointer(screen,engine,()->{if(verificationCode!=null)verificationCode.requestFocus();else query.requestFocus();});
        engine.setVisibility(View.INVISIBLE);
        engine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        WebSettings settings = engine.getSettings();
        defaultUserAgent = settings.getUserAgentString();
        configureSource();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(engine, true);
        resultsView = new ScrollView(this);
        grid = new GridLayout(this);
        grid.setColumnCount(television?4:2);
        grid.setClipChildren(false);
        resultsView.setClipToPadding(false);
        grid.setPadding(dp(8), 0, dp(8), dp(16));
        resultsView.addView(grid);
        workspace.addView(resultsView, new FrameLayout.LayoutParams(-1, -1));
        if(television&&androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT))
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(engine,asset("tv-media.js"),UrlPolicy.mediaOrigins());
        engine.setWebChromeClient(new WebChromeClient());
        engine.setDownloadListener((url, userAgent, disposition, mime, length) -> {
            diagnostics.add("search.download_requested", url, 0);
            if (searchRunning) failSearch("Serwis zwrócił plik zamiast strony wyników. Wybierz Raport.");
        });
        engine.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return !"https".equals(request.getUrl().getScheme());
                String target = request.getUrl().toString();
                if(google!=null)return true;
                if (selectedSource == SiteSource.FREEDISC && SiteSource.isMobileFreediscUrl(target)) {
                    diagnostics.add("search.mobile_redirect", null, 0);
                    if (searchRunning && !mobileRedirectRetried) {
                        mobileRedirectRetried = true;
                        freediscRouted = false;
                        freediscRouteAttempts = 0;
                        final int generation = requestGeneration;
                        handler.post(() -> {
                            if (searchRunning && generation == requestGeneration) {
                                configureSource();
                                engine.loadUrl(selectedSource.home + "/");
                            }
                        });
                    } else if (searchRunning) failSearch("Freedisc ponownie przekierowuje do wersji mobilnej. Zapisz Raport.");
                    return true;
                }
                // A background navigation can never open a movie or launch another app.
                boolean blocked = SiteSource.fromUrl(target) != selectedSource;
                if (blocked) {
                    diagnostics.add("search.navigation_blocked", target, 0);
                    diagnostics.add("search.blocked_kind." + UrlPolicy.pageKind(target), null, 0);
                }
                return blocked;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                diagnostics.add("search.page_started", url, 0);
                diagnostics.add("search.started_kind." + UrlPolicy.pageKind(url), null, 0);
                if (searchRunning && SiteSource.fromUrl(url) == selectedSource) navigationStarted = true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if(google!=null)return;
                CookieManager.getInstance().flush();
                diagnostics.add("search.page", url, 0);
                diagnostics.add("search.finished_kind." + UrlPolicy.pageKind(url), null, 0);
                diagnostics.add("search.current_kind." + UrlPolicy.pageKind(view.getUrl()), null, 0);
                // A stale about:blank completion may arrive after a new load. Inspect the
                // actual current document only after this request has begun navigating.
                if ((searchRunning && navigationStarted || verificationPanel != null)
                        && SiteSource.fromUrl(view.getUrl()) == selectedSource) inspect(++scanToken, 0);
                else if (searchRunning) previewButton.setVisibility(View.VISIBLE);
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame()) {
                    lastHttpError = response.getStatusCode();
                    diagnostics.add("search.http_error", request.getUrl().toString(), lastHttpError);
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    if(fallbackUsed)return;
                    diagnostics.add("search.network_error", request.getUrl().toString(), error.getErrorCode());
                    if (searchRunning) failSearch("Nie udało się połączyć z " + selectedSource.label
                        + ". Wybierz Pokaż stronę lub zapisz Raport.");
                } else diagnostics.add("search.resource_error", request.getUrl().toString(), error.getErrorCode());
            }
        });
        query.setOnEditorActionListener((view, action, event) -> {
            if(television)diagnostics.add("search.editor_action",null,action);
            boolean enter=event!=null&&(event.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER||event.getKeyCode()==android.view.KeyEvent.KEYCODE_NUMPAD_ENTER||event.getKeyCode()==android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            // TV keyboards also report IME_NULL/NONE or a DOWN event for their confirmation key.
            if(action==EditorInfo.IME_ACTION_SEARCH||television&&(event==null||enter)){search();return true;}
            return false;
        });
        sourcePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectSource(SiteSource.values()[position]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        if(television){
            tvRows.add(java.util.Arrays.asList(sourcePicker));tvRows.add(java.util.Arrays.asList(query,searchButton));
            tvRows.add(children(actions));tvRows.add(children(collections));tvRows.add(java.util.Arrays.asList(previewButton));
            searchContent.getViewTreeObserver().addOnGlobalLayoutListener(this::wireSearchNavigation);
            query.post(query::requestFocus);
        }
        if (saved != null) {
            query.setText(saved.getString("query", ""));
            try { results = new JSONArray(saved.getString("results", "[]")); } catch (Exception ignored) { }
            requestedSearch = saved.getString("requestedSearch");
            searchReturnUsed = saved.getBoolean("searchReturnUsed");
            engine.restoreState(saved);
            searchResults=results;
            if (results.length() > 0) render();
            else if (!query.getText().toString().trim().isEmpty()&&"results".equals(saved.getString("collection","results"))) search();
        }
        if(saved==null)applyEpisodeQuery(getIntent());
        String initialCollection=saved!=null?saved.getString("collection","results"):getIntent().getStringExtra("collection");
        if(initialCollection!=null&&!"results".equals(initialCollection))showCollection(initialCollection);
    }
    private static java.util.List<View> children(android.view.ViewGroup row){java.util.List<View> views=new java.util.ArrayList<>();for(int i=0;i<row.getChildCount();i++)views.add(row.getChildAt(i));return views;}
    private void wireSearchNavigation(){
        if(!television||verificationPanel!=null)return;
        java.util.List<java.util.List<View>> live=TvFocus.connect(tvRows);
        tvLastRow=live.isEmpty()?new java.util.ArrayList<>():live.get(live.size()-1);
        if(grid.getChildCount()>0){
            for(int i=0;i<tvLastRow.size();i++)tvLastRow.get(i).setNextFocusDownId(grid.getChildAt(Math.min(grid.getChildCount()-1,Math.round(i*3f/Math.max(1,tvLastRow.size()-1)))).getId());
            query.setNextFocusDownId(grid.getChildAt(0).getId());searchButton.setNextFocusDownId(grid.getChildAt(0).getId());
            for(int i=0;i<Math.min(4,grid.getChildCount());i++)if(!tvLastRow.isEmpty())grid.getChildAt(i).setNextFocusUpId(TvFocus.column(tvLastRow,i,4).getId());
        }
    }
    private void focusAboveGrid(int column){wireSearchNavigation();if(!tvLastRow.isEmpty())TvFocus.column(tvLastRow,column,4).requestFocus();}
    private void togglePointer(){
        if(pointer!=null){boolean on=pointer.toggle();Toast.makeText(this,on?"Kursor: strzałki przesuwają · OK klika · Wstecz wyłącza":"Kursor wyłączony",Toast.LENGTH_SHORT).show();}
    }
    private void showCollection(String selected){
        if("results".equals(collection))searchResults=results;
        collection="history".equals(selected)||"favorites".equals(selected)?selected:"results";
        stopSearch();engine.stopLoading();dismissVerification();selectedResultIndex=0;
        results="results".equals(collection)?searchResults:library.items(collection);
        render();
    }
    private void clearCollection(){
        if("results".equals(collection))return;
        final String selected=collection;
        new AlertDialog.Builder(this).setTitle("history".equals(selected)?"Wyczyścić historię?":"Wyczyścić ulubione?")
            .setMessage("Pozycje zostaną usunięte tylko z tej listy na tym urządzeniu.")
            .setPositiveButton("Wyczyść",(d,w)->{library.clear(selected);showCollection(selected);})
            .setNegativeButton("Anuluj",null).show();
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        String selected=intent.getStringExtra("collection");
        if(intent.hasExtra("searchQuery"))applyEpisodeQuery(intent);else showCollection(selected);
    }
    private void applyEpisodeQuery(Intent intent){
        String title=intent.getStringExtra("searchQuery");if(title==null||title.trim().isEmpty())return;
        SiteSource source=SiteSource.restore(intent.getStringExtra("searchSource"));
        selectSource(source);sourcePicker.setSelection(source.ordinal());
        query.setText(title.substring(0,Math.min(title.length(),220)));lastSearchAt=0;query.post(this::search);
    }
    private void cardActions(JSONObject item){
        final String url=item.optString("url");
        boolean favorite=library.favorite(url);
        String[] options="history".equals(collection)?new String[]{favorite?"Usuń z ulubionych":"Dodaj do ulubionych","Usuń z historii"}:new String[]{favorite?"Usuń z ulubionych":"Dodaj do ulubionych"};
        MovieLibrary.Item saved=library.find(url);
        if(saved!=null&&saved.position>0){String[] more=java.util.Arrays.copyOf(options,options.length+1);more[options.length]="Od początku";options=more;}
        final int restartIndex=options.length-1;
        final boolean canRestart=saved!=null&&saved.position>0;
        new AlertDialog.Builder(this).setTitle(item.optString("title")).setItems(options,(d,which)->{
            if(canRestart&&which==restartIndex){openMovie(url,true);return;}
            if(which==0)library.toggle(url,item.optString("title"),item.optString("thumbnail"));
            else library.remove("history",url);
            int previousIndex=selectedResultIndex;showCollection(collection);selectedResultIndex=previousIndex;
        }).setNegativeButton("Zamknij",null).show();
    }
    private void selectSource(SiteSource source) {
        if (source == selectedSource) return;
        stopSearch();
        engine.stopLoading();
        dismissVerification();
        previewButton.setVisibility(View.GONE);
        selectedSource = source;
        collection="results";clearCollection.setVisibility(View.GONE);
        configureSource();
        requestedSearch = null;
        searchReturnUsed = false;
        lastSearchAt = 0;
        results = new JSONArray();searchResults=results;
        selectedResultIndex=0;
        if(television){query.setNextFocusDownId(View.NO_ID);searchButton.setNextFocusDownId(View.NO_ID);}
        thumbnails.nextGeneration();
        grid.removeAllViews();
        query.setHint("Tytuł filmu, np. " + source.example);
        getSharedPreferences("sources", MODE_PRIVATE).edit().putString("selected", source.name()).apply();
        status.setText("Źródło: " + source.label + ". Wpisz tytuł i wybierz Szukaj.");
    }
    private void configureSource() {
        WebSettings settings = engine.getSettings();
        settings.setUserAgentString(selectedSource.userAgent(defaultUserAgent));
        settings.setUseWideViewPort(selectedSource == SiteSource.FREEDISC);
        settings.setLoadWithOverviewMode(selectedSource == SiteSource.FREEDISC);
    }
    private void search() {
        String title = query.getText().toString().trim();
        if (title.isEmpty()) { query.setError("Wpisz tytuł filmu"); return; }
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastSearchAt != 0 && now - lastSearchAt < 2000) {
            if(!searchRunning)status.setText("Poczekaj chwilę przed kolejnym wyszukiwaniem."); return;
        }
        collection="results";clearCollection.setVisibility(View.GONE);
        stopSearch();
        engine.stopLoading();
        dismissVerification();
        lastSearchAt = now;
        requestedTitle=title;fallbackUsed=false;previewButton.setText("Pokaż stronę");
        lastHttpError = 0;
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(), 0);
        query.clearFocus();
        results = new JSONArray();searchResults=results;
        selectedResultIndex=0;
        if(television){query.setNextFocusDownId(View.NO_ID);searchButton.setNextFocusDownId(View.NO_ID);}
        thumbnails.nextGeneration();
        grid.removeAllViews();
        resultsView.setVisibility(View.VISIBLE);
        engine.setVisibility(View.INVISIBLE);
        status.setText("Szukam w " + selectedSource.label + "…");
        previewButton.setVisibility(View.GONE);
        searchRunning = true;
        navigationStarted = false;
        mobileRedirectRetried = false;
        freediscRouted = false;
        freediscRouteAttempts = 0;
        configureSource();
        // Advertised by the site's public /opensearch.xml, not a guessed endpoint.
        requestedSearch = selectedSource.searchUrl(title);
        searchReturnUsed = false;
        diagnostics.clear();
        diagnostics.add("search.start", requestedSearch, 0);
        diagnostics.add("search.source." + selectedSource.name().toLowerCase(java.util.Locale.ROOT), null, 0);
        armSearchTimeout();
        // Freedisc's own search button routes from its initialized desktop page.
        if(forceGoogle){startGoogle();return;}
        engine.loadUrl(selectedSource == SiteSource.FREEDISC ? selectedSource.home + "/" : requestedSearch);
    }
    private void routeFreedisc(int token, int attempt) {
        if (freediscRouted) {
            failSearch("Freedisc wrócił na stronę główną po uruchomieniu wyszukiwania. Wybierz Pokaż stronę lub zapisz Raport.");
            return;
        }
        if (++freediscRouteAttempts > 20) {
            diagnostics.add("search.router_unavailable", engine.getUrl(), 0);
            failSearch("Nie uruchomił się moduł wyszukiwarki Freedisc. Wybierz Pokaż stronę lub zapisz Raport.");
            return;
        }
        String script = asset("freedisc-search.js").replace("__VP_SEARCH_URL__", JSONObject.quote(requestedSearch));
        engine.evaluateJavascript(script, result -> {
            if (!active || token != scanToken || !searchRunning) return;
            if ("\"routed\"".equals(result)) {
                freediscRouted = true;
                diagnostics.add("search.client_route", requestedSearch, 0);
            } else if ("\"invalid\"".equals(result) || "\"error\"".equals(result)) {
                diagnostics.add("search.client_route_error", null, 0);
                failSearch("Nie udało się uruchomić wyszukiwania Freedisc. Zapisz Raport.");
                return;
            }
            handler.postDelayed(() -> inspect(token, attempt), 1000);
        });
    }
    private void stopSearch() {
        if(google!=null){GoogleFallback previous=google;google=null;previous.dispose();}
        searchRunning = false;
        ++requestGeneration;
        ++scanToken;
        pauseSearchTimeout();
    }
    private void pauseSearchTimeout() {
        if (searchTimeout != null) handler.removeCallbacks(searchTimeout);
        searchTimeout = null;
    }
    private void armSearchTimeout() {
        if (!active || !searchRunning || searchTimeout != null) return;
        final int generation = requestGeneration;
        searchTimeout = () -> {
            if (!active || !searchRunning || generation != requestGeneration) return;
            diagnostics.add("search.timeout", engine.getUrl(), engine.getProgress());
            diagnostics.add("search.timeout_kind." + UrlPolicy.pageKind(engine.getUrl()), null, 0);
            failSearch("Nie udało się odczytać wyników z " + selectedSource.label
                + " w ciągu 30 sekund. Wybierz Pokaż stronę lub zapisz Raport.");
        };
        handler.postDelayed(searchTimeout, SEARCH_TIMEOUT_MS);
    }
    private void failSearch(String message) {
        if(searchRunning&&!fallbackUsed&&requestedTitle!=null){startGoogle();return;}
        stopSearch();
        engine.stopLoading();
        dismissVerification();
        previewButton.setText("Pokaż stronę");
        previewButton.setVisibility(requestedSearch == null ? View.GONE : View.VISIBLE);
        status.setText(message);
    }
    private void startGoogle(){
        if(fallbackUsed||requestedTitle==null)return;
        fallbackUsed=true;++scanToken;++requestGeneration;pauseSearchTimeout();engine.stopLoading();dismissVerification();
        final int generation=requestGeneration;final SiteSource source=selectedSource;
        status.setText("Wyszukiwarka "+source.label+" niedostępna — szukam przez Google…");previewButton.setVisibility(View.GONE);
        google=new GoogleFallback(this,screen,source,diagnostics,new GoogleFallback.Listener(){
            private boolean valid(){return active&&searchRunning&&generation==requestGeneration&&source==selectedSource;}
            @Override public void results(JSONArray rows){if(!valid())return;results=rows;searchResults=rows;collection="results";render();status.setText("Wyniki Google · "+source.label+" · "+rows.length());}
            @Override public void access(){if(!valid())return;previewButton.setText("Potwierdzenie Google");previewButton.setVisibility(View.VISIBLE);status.setText("Google wymaga ręcznego potwierdzenia. Zapytanie pozostaje bez zmian.");google.showAccess();}
            @Override public void closed(){if(valid()){previewButton.setVisibility(View.VISIBLE);status.setText("Google czeka na potwierdzenie — wybierz Potwierdzenie Google lub rozpocznij nowe wyszukiwanie.");}}
            @Override public void failed(String message){if(valid())failSearch(message);}
        });
        google.start(requestedTitle);
    }
    private void retryInspection() {
        if (!searchRunning) { searchRunning = true; ++requestGeneration; }
        armSearchTimeout();
        inspect(++scanToken, 0);
    }
    private void previewSearch() {
        if(google!=null){google.showAccess();return;}
        if (requestedSearch == null || SiteSource.fromUrl(requestedSearch) != selectedSource) return;
        diagnostics.add("search.preview_open", engine.getUrl(), 0);
        searchRunning = true;
        ++requestGeneration;
        pauseSearchTimeout();
        if (selectedSource == SiteSource.FREEDISC && UrlPolicy.isHome(engine.getUrl())) {
            freediscRouted = false;
            freediscRouteAttempts = 0;
        }
        showVerification("preview");
        armSearchTimeout();
        if (SiteSource.fromUrl(engine.getUrl()) != selectedSource) {
            navigationStarted = false;
            if (selectedSource == SiteSource.FREEDISC) { freediscRouted = false; freediscRouteAttempts = 0; }
            configureSource();
            engine.loadUrl(selectedSource == SiteSource.FREEDISC ? selectedSource.home + "/" : requestedSearch);
        } else retryInspection();
    }
    private void inspect(int token, int attempt) {
        if (google!=null)return;
        if (!active || token != scanToken || (!searchRunning && verificationPanel == null)
                || SiteSource.fromUrl(engine.getUrl()) != selectedSource) return;
        engine.evaluateJavascript(asset("search-state.js"), value -> {
            if (!active || token != scanToken) return;
            String stage = "results";
            try {
                JSONObject state = new JSONObject((String) new JSONTokener(value).nextValue());
                stage = state.optString("stage", "results");
                if (attempt == 0) diagnostics.add("search.dom_links", null, Math.min(10000, state.optInt("anchors")));
            } catch (Exception ignored) { }
            if (!"results".equals(stage)) {
                diagnostics.add("search.verification_required", null,
                    "verification".equals(stage) ? 1 : ("consent".equals(stage) ? 2 : 3));
                status.setText("Serwis wymaga potwierdzenia. Po nim wrócimy do miniatur.");
                pauseSearchTimeout();
                showVerification(stage);
                if (attempt < 120) handler.postDelayed(() -> inspect(token, attempt + 1), 1500);
                return;
            }
            armSearchTimeout();
            if (UrlPolicy.isHome(engine.getUrl()) && requestedSearch != null) {
                if (selectedSource == SiteSource.FREEDISC) { routeFreedisc(token, attempt); return; }
                if (!searchReturnUsed) {
                    searchReturnUsed = true;
                    CookieManager.getInstance().flush();
                    diagnostics.add("search.return_after_home", requestedSearch, 0);
                    engine.loadUrl(requestedSearch);
                } else failSearch("Serwis przekierował wyszukiwanie na stronę główną. Wybierz Pokaż stronę lub zapisz Raport.");
                return;
            }
            readCards(token, attempt);
        });
    }
    private void readCards(int token, int attempt) {
        engine.evaluateJavascript(asset("search-results.js"), value -> {
            if (!active || token != scanToken) return;
            try {
                Object decoded = new JSONTokener(value).nextValue();
                if (decoded instanceof String && ((String) decoded).length() < 600000) {
                    JSONArray input = new JSONArray((String) decoded);
                    JSONArray accepted = new JSONArray();
                    java.util.HashSet<String> seen = new java.util.HashSet<>();
                    for (int i = 0; i < Math.min(input.length(), 24); i++) {
                        JSONObject item = input.optJSONObject(i);
                        if (item == null || !UrlPolicy.isMovie(item.optString("url"))
                                || SiteSource.fromUrl(item.optString("url")) != selectedSource) continue;
                        String key=MovieLibrary.key(item.optString("url"));
                        if (!seen.add(key)) continue;
                        String name = item.optString("title").replaceAll("[\\p{Cntrl}]", " ").trim();
                        if (name.isEmpty()) continue;
                        item.put("title", name.substring(0, Math.min(180, name.length())));
                        accepted.put(item);
                    }
                    if (accepted.length() > 0) {
                        diagnostics.add("search.cards", null, accepted.length());
                        results = accepted; searchResults=accepted; collection="results"; render(); return;
                    }
                }
            } catch (Exception ignored) { diagnostics.add("search.parse_error", null, 0); }
            if (attempt < 12) {
                handler.postDelayed(() -> inspect(token, attempt + 1), 1500);
            } else {
                diagnostics.add("search.empty_or_unrecognized", engine.getUrl(), 0);
                engine.evaluateJavascript(asset("search-outcome.js"),raw->{
                    if(!active||token!=scanToken||google!=null)return;
                    try{JSONObject outcome=new JSONObject((String)new JSONTokener(raw).nextValue());if(outcome.optBoolean("empty")&&!outcome.optBoolean("loading")&&lastHttpError<400){results=new JSONArray();searchResults=results;render();return;}}catch(Exception ignored){}
                    failSearch("Wyszukiwarka serwisu nie zwróciła rozpoznanych wyników.");
                });
            }
        });
    }
    private void showVerification(String stage) {
        if (!active) return;
        if (!"preview".equals(stage)) pauseSearchTimeout();
        if (verificationPanel != null) {
            if (stage.equals(verificationStage)) return;
            dismissVerification();
        }
        verificationStage = stage;
        if(television)((TvWebView)engine).codePage("verification".equals(stage),this::submitPageCode);
        android.view.ViewGroup parent = (android.view.ViewGroup) engine.getParent();
        if (parent != null) parent.removeView(engine);
        searchContent.setVisibility(View.GONE);
        verificationPanel = new LinearLayout(this);
        verificationPanel.setOrientation(LinearLayout.VERTICAL);
        verificationPanel.setBackgroundColor(Color.rgb(17, 24, 32));
        TextView heading = text("preview".equals(stage) ? "Strona wyszukiwania · " + selectedSource.label
            : "Potwierdzenie wyszukiwania", 20, Color.WHITE);
        heading.setPadding(dp(16), dp(12), dp(16), dp(4));
        verificationPanel.addView(heading);
        TextView explanation = text("preview".equals(stage)
            ? "To strona zwrócona przez serwis. Jeśli wymaga potwierdzenia, dokończ je tutaj. Sprawdź ponawia odczyt miniatur, Anuluj wraca do wyszukiwania."
            : "verification".equals(stage)
            ? selectedSource.label + " sprawdza dostęp już przy wyszukiwaniu. Wpisz kod w polu aplikacji, wybierz „Wstaw kod”, potem przycisk na stronie."
            : "Dokończ wymagany przez " + selectedSource.label + " krok. Potem wrócimy do miniatur.", 13, Color.LTGRAY);
        explanation.setPadding(dp(16), 0, dp(16), dp(8));
        verificationPanel.addView(explanation);
        LinearLayout actions = new LinearLayout(this);
        addButton(actions, "Klawiatura", this::showVerificationKeyboard);
        addButton(actions, "Sprawdź", this::retryInspection);
        addButton(actions, "Anuluj", this::cancelVerification);
        if(television){addButton(actions,"Strona",()->{engine.requestFocus();});addButton(actions,"Kursor",this::togglePointer);}
        verificationPanel.addView(engine, new LinearLayout.LayoutParams(-1, 0, 1));
        if ("verification".equals(stage)) {
            LinearLayout codeRow = new LinearLayout(this);
            codeRow.setPadding(dp(8), dp(4), dp(8), 0);
            verificationCode = new EditText(this);
            verificationCode.setHint("Kod z obrazka — wpisz tutaj");
            verificationCode.setSingleLine(true);
            TvUi.editor(verificationCode,()->applyVerificationCode(true));
            verificationCode.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            verificationCode.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
            verificationCode.setImeOptions(EditorInfo.IME_ACTION_DONE);
            verificationCode.setOnEditorActionListener((view, action, event) -> {
                if (action == EditorInfo.IME_ACTION_DONE || television&&action==EditorInfo.IME_ACTION_GO) { applyVerificationCode(television); return true; }
                return false;
            });
            codeRow.addView(verificationCode, new LinearLayout.LayoutParams(0, dp(56), 1));
            if(!television)addButton(codeRow, "Wstaw kod", this::applyVerificationCode);
            if(television)addButton(codeRow,"Zatwierdź",()->applyVerificationCode(true));
            verificationPanel.addView(codeRow);
            if(television)TvFocus.connect(java.util.Arrays.asList(children(codeRow),children(actions)));
        }else if(television)TvFocus.connect(java.util.Arrays.asList(children(actions)));
        verificationPanel.addView(actions);
        screen.addView(verificationPanel, new FrameLayout.LayoutParams(-1, -1));
        engine.setVisibility(View.VISIBLE);
        engine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        engine.setFocusable(true);
        engine.setFocusableInTouchMode(true);
        // Keep the editor in the Activity's window. AlertDialog can mark WebView-only
        // content ALT_FOCUSABLE_IM before an HTML input becomes the active editor.
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(verificationCode==null)engine.requestFocus();else verificationCode.requestFocus();
        diagnostics.add("search.verification_open", null, 0);
        if ("verification".equals(stage)) {
            engine.getSettings().setUseWideViewPort(false);
            engine.getSettings().setLoadWithOverviewMode(false);
            engine.evaluateJavascript(asset("verification-layout.js"), null);
            engine.postDelayed(()->{if(verificationCode!=null&&verificationCode.length()==0&&(pointer==null||!pointer.active()))showVerificationKeyboard();},250);
        }
    }
    private void showVerificationKeyboard() {
        if (!active || verificationPanel == null || !UrlPolicy.allowed(engine.getUrl())) return;
        if (verificationCode != null) {
            if(television)verificationCode.setShowSoftInputOnFocus(true);
            verificationCode.requestFocus();
            verificationCode.post(() -> {
                if (!active || verificationCode == null) return;
                InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                boolean shown = input.showSoftInput(verificationCode, InputMethodManager.SHOW_IMPLICIT);
                if (android.os.Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
                    getWindow().getInsetsController().show(android.view.WindowInsets.Type.ime());
                }
                diagnostics.add("search.native_keyboard_requested", null, shown ? 1 : 0);
            });
            return;
        }
        engine.requestFocus();
        engine.evaluateJavascript(asset("verification-focus.js"), result -> {
            if (!"true".equals(result) || !active || verificationPanel == null) return;
            // Wait for Chromium's HTML input connection before asking Android for the IME.
            engine.postDelayed(() -> {
                if (!active || verificationPanel == null || !engine.hasWindowFocus()) return;
                InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                boolean shown = input.showSoftInput(engine, InputMethodManager.SHOW_IMPLICIT);
                diagnostics.add("search.keyboard_requested", null, shown ? 1 : 0);
            }, 200);
        });
    }
    private void submitPageCode(){
        if(!active||verificationPanel==null||!UrlPolicy.allowed(engine.getUrl()))return;
        engine.evaluateJavascript(asset("verification-submit.js"),sent->{
            if("true".equals(sent)){
                if(pointer!=null)pointer.stop();
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(engine.getWindowToken(),0);
                diagnostics.add("search.page_code_confirmed",engine.getUrl(),0);retryInspection();
            }else Toast.makeText(this,"Wpisz kod z obrazka przed zatwierdzeniem.",Toast.LENGTH_SHORT).show();
        });
    }
    private void applyVerificationCode() { applyVerificationCode(false); }
    private void applyVerificationCode(boolean submit) {
        if (verificationCode == null || !UrlPolicy.allowed(engine.getUrl())) return;
        String code = verificationCode.getText().toString();
        if (code.isEmpty()) { verificationCode.setError("Wpisz kod z obrazka"); return; }
        String javascript = asset("verification-fill.js").replace("__VP_MANUAL_CODE__", JSONObject.quote(code));
        engine.evaluateJavascript(javascript, result -> {
            if (verificationPanel == null) return;
            diagnostics.add("search.manual_code_inserted", null, "true".equals(result) ? 1 : 0);
            if ("true".equals(result)) {
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(verificationCode.getWindowToken(), 0);
                if(submit)engine.evaluateJavascript(asset("verification-submit.js"), sent->{
                    Toast.makeText(this,"true".equals(sent)?"Kod zatwierdzony przyciskiem serwisu.":"Nie znaleziono przycisku. Wybierz Strona i użyj przycisku serwisu.",Toast.LENGTH_LONG).show();
                    handler.postDelayed(this::retryInspection,1000);
                });
                else Toast.makeText(this, "Kod wstawiony. Użyj przycisku „Wchodzę” na stronie.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Nie znaleziono pola kodu na stronie. Spróbuj dotknąć go bezpośrednio.", Toast.LENGTH_LONG).show();
            }
        });
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!television && focused && active && verificationCode != null && verificationCode.length()==0) verificationCode.post(this::showVerificationKeyboard);
    }
    private void cancelVerification() {
        stopSearch();
        engine.stopLoading();
        diagnostics.add("search.verification_cancelled", null, 0);
        dismissVerification();
        previewButton.setVisibility(requestedSearch == null ? View.GONE : View.VISIBLE);
        status.setText("Widok strony zamknięty. Możesz ponowić wyszukiwanie lub zapisać Raport.");
    }
    private void dismissVerification() {
        if (verificationPanel == null) return;
        if(pointer!=null)pointer.stop();
        if(television)((TvWebView)engine).codePage(false,null);
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(engine.getWindowToken(), 0);
        android.view.ViewGroup owner = (android.view.ViewGroup) engine.getParent();
        if (owner != null) owner.removeView(engine);
        screen.removeView(verificationPanel);
        verificationPanel = null;
        verificationStage = null;
        configureSource();
        if (verificationCode != null) verificationCode.setText("");
        verificationCode = null;
        engine.clearFocus();
        engineHost.addView(engine, new FrameLayout.LayoutParams(-1, -1));
        engine.setVisibility(View.INVISIBLE);
        engine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        searchContent.setVisibility(View.VISIBLE);
        CookieManager.getInstance().flush();
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if(google!=null&&google.key(event))return true;
        if(pointer!=null&&pointer.key(event))return true;
        if(television&&verificationPanel!=null&&event.getKeyCode()==android.view.KeyEvent.KEYCODE_MENU){if(event.getAction()==android.view.KeyEvent.ACTION_UP)togglePointer();return true;}
        if(television&&verificationPanel!=null&&engine.hasFocus()){
            int key=event.getKeyCode();
            if(key==android.view.KeyEvent.KEYCODE_DPAD_UP||key==android.view.KeyEvent.KEYCODE_DPAD_LEFT
                ||key==android.view.KeyEvent.KEYCODE_DPAD_DOWN||key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){
                if(event.getAction()==android.view.KeyEvent.ACTION_UP){
                    int meta=(key==android.view.KeyEvent.KEYCODE_DPAD_UP||key==android.view.KeyEvent.KEYCODE_DPAD_LEFT)?android.view.KeyEvent.META_SHIFT_ON:0;
                    engine.dispatchKeyEvent(new android.view.KeyEvent(0,0,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_TAB,0,meta));
                    engine.dispatchKeyEvent(new android.view.KeyEvent(0,0,android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_TAB,0,meta));
                }return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() {
        if(google!=null&&google.back())return;
        if(pointer!=null&&pointer.active()){pointer.stop();return;}
        if (verificationPanel != null) cancelVerification();
        else super.onBackPressed();
    }
    private void render() {
        stopSearch();
        previewButton.setVisibility(View.GONE);
        thumbnails.nextGeneration();
        dismissVerification();
        engine.setVisibility(View.INVISIBLE);
        resultsView.setVisibility(View.VISIBLE);
        grid.removeAllViews();
        clearCollection.setVisibility("results".equals(collection)?View.GONE:View.VISIBLE);
        String heading="history".equals(collection)?"Historia":"favorites".equals(collection)?"Ulubione":"Znalezione filmy";
        status.setText(heading+" · "+results.length()+(results.length()==0?" · lista jest pusta":television?" · OK: film · przytrzymaj OK: opcje":" · dotknij: film · przytrzymaj: opcje"));
        int columns=television?4:2;
        int width = (getResources().getDisplayMetrics().widthPixels - dp(television?64+16+columns*12:40)) / columns;
        int imageHeight=television?width*9/16:dp(116);
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.optJSONObject(i);
            if (result == null) continue;
            String url = result.optString("url");
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.rgb(29, 40, 53));
            background.setCornerRadius(dp(12));
            card.setBackground(background);
            TvUi.focusable(card);
            card.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = width;
            params.height = television?imageHeight+dp(80):dp(204);
            params.setMargins(dp(6), dp(6), dp(6), dp(6));
            grid.addView(card, params);
            FrameLayout art = new FrameLayout(this);
            card.addView(art, new LinearLayout.LayoutParams(-1, imageHeight));
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(36, 61, 72));
            image.setContentDescription(result.optString("title"));
            art.addView(image, new FrameLayout.LayoutParams(-1, -1));
            TextView play = text("▶", 32, Color.WHITE);
            play.setGravity(Gravity.CENTER);
            art.addView(play, new FrameLayout.LayoutParams(-1, -1));
            TextView name = text(result.optString("title"), television?16:14, Color.WHITE);
            name.setMaxLines(3);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setPadding(dp(10), dp(8), dp(10), dp(4));
            card.addView(name);
            TextView host = text(Uri.parse(url).getHost(), 11, Color.rgb(114, 220, 196));
            MovieLibrary.Item saved=library.find(url);
            if(saved!=null&&saved.position>0)host.setText("Wznów · "+MovieLibrary.clock(saved.position)+" / "+MovieLibrary.clock(saved.duration));
            else if(saved!=null&&saved.completed)host.setText("Obejrzano · od początku");
            if(library.favorite(url))host.setText("★ "+host.getText());
            host.setPadding(dp(10), 0, dp(10), dp(8));
            card.addView(host);
            card.setContentDescription("Odtwórz " + result.optString("title")+" · "+host.getText());
            final int index=i;
            card.setOnClickListener(v -> {selectedResultIndex=index;openMovie(url);});
            card.setOnLongClickListener(v->{selectedResultIndex=index;cardActions(result);return true;});
            thumbnails.load(image, url, result.optString("thumbnail"), engine.getSettings().getUserAgentString());
        }
        if(television){
            for(int i=0;i<grid.getChildCount();i++){
                View card=grid.getChildAt(i);
                card.setNextFocusLeftId(i%columns>0?grid.getChildAt(i-1).getId():card.getId());
                card.setNextFocusRightId(i%columns<columns-1&&i+1<grid.getChildCount()?grid.getChildAt(i+1).getId():card.getId());
                card.setNextFocusUpId(i>=columns?grid.getChildAt(i-columns).getId():View.NO_ID);
                card.setNextFocusDownId(i+columns<grid.getChildCount()?grid.getChildAt(i+columns).getId():card.getId());
                final int position=i;
                card.setOnKeyListener((v,key,event)->{
                    if(key==android.view.KeyEvent.KEYCODE_MENU||key==android.view.KeyEvent.KEYCODE_INFO){if(event.getAction()==android.view.KeyEvent.ACTION_UP)v.performLongClick();return true;}
                    if(key==android.view.KeyEvent.KEYCODE_DPAD_CENTER||key==android.view.KeyEvent.KEYCODE_ENTER){
                        if(event.getAction()==android.view.KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)v.setTag(null);
                        if(event.getAction()==android.view.KeyEvent.ACTION_DOWN&&event.getRepeatCount()==1){v.setTag(Boolean.TRUE);v.performLongClick();}
                        if(event.getAction()==android.view.KeyEvent.ACTION_UP){if(!Boolean.TRUE.equals(v.getTag()))v.performClick();v.setTag(null);}
                        return true;
                    }
                    int target=position;
                    if(key==android.view.KeyEvent.KEYCODE_DPAD_UP)target=position-columns;
                    else if(key==android.view.KeyEvent.KEYCODE_DPAD_DOWN)target=position+columns<grid.getChildCount()?position+columns:position;
                    else if(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT)target=position%columns>0?position-1:position;
                    else if(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT)target=position%columns<columns-1&&position+1<grid.getChildCount()?position+1:position;
                    else return false;
                    if(event.getAction()==android.view.KeyEvent.ACTION_DOWN){if(target<0)focusAboveGrid(position%columns);else grid.getChildAt(target).requestFocus();}
                    return true;
                });
            }
            if(grid.getChildCount()>0){
                wireSearchNavigation();
                grid.post(()->{if(grid.getChildCount()>0)grid.getChildAt(Math.min(selectedResultIndex,grid.getChildCount()-1)).requestFocus();});
            }
        }
    }
    private void openMovie(String url) {openMovie(url,false);}
    private void openMovie(String url,boolean fromStart) {
        if (!UrlPolicy.isMovie(url)) return;
        CookieManager.getInstance().flush();
        String title="",thumbnail="";
        for(int i=0;i<results.length();i++){JSONObject item=results.optJSONObject(i);if(item!=null&&MovieLibrary.key(url).equals(MovieLibrary.key(item.optString("url")))){title=item.optString("title");thumbnail=item.optString("thumbnail");break;}}
        library.opened(url,title,thumbnail);
        startActivity(new Intent(this, MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(url)).putExtra("movieTitle",title).putExtra("movieThumbnail",thumbnail).putExtra("fromStart",fromStart));
    }
    private void exportReport() {
        diagnostics.add("search.report_export_requested", engine.getUrl(), engine.getProgress());
        diagnostics.add("search.export_kind." + UrlPolicy.pageKind(engine.getUrl()), null, searchRunning ? 1 : 0);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "videoplayer-wyszukiwanie.json");
        try { startActivityForResult(intent, EXPORT_REPORT); }
        catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Nie znaleziono miejsca zapisu raportu.", Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_REPORT || result != RESULT_OK || data == null || data.getData() == null) return;
        try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (out == null) throw new java.io.IOException();
            out.write(diagnostics.export().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Zapisano raport wyszukiwania.", Toast.LENGTH_LONG).show();
        } catch (java.io.IOException | SecurityException e) {
            Toast.makeText(this, "Nie udało się zapisać raportu.", Toast.LENGTH_LONG).show();
        }
    }
    private void pasteLink() {
        EditText input = new EditText(this);
        input.setHint("Link filmu z Vider lub Freedisc");
        TvUi.editor(input);
        new AlertDialog.Builder(this).setTitle("Otwórz link filmu").setView(input)
            .setPositiveButton("Otwórz", (d, w) -> {
                String url = input.getText().toString().trim();
                if (UrlPolicy.isMovie(url)) openMovie(url);
                else Toast.makeText(this, "Podaj link HTTPS do filmu z Vider lub Freedisc.", Toast.LENGTH_LONG).show();
            }).setNegativeButton("Anuluj", null).show();
    }
    private String asset(String name) {
        try (java.io.InputStream in = getAssets().open(name); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { return "'[]'"; }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("selectedResultIndex",selectedResultIndex);
        out.putString("source", selectedSource.name());
        out.putString("query", query.getText().toString());
        out.putString("requestedSearch", requestedSearch);
        out.putBoolean("searchReturnUsed", searchReturnUsed);
        out.putString("results", ("results".equals(collection)?results:searchResults).toString());
        out.putString("collection",collection);
        engine.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override protected void onPause() {
        active = false; if(pointer!=null)pointer.stop(); pauseSearchTimeout(); handler.removeCallbacksAndMessages(null);
        if(google!=null)google.pause();
        engine.onPause(); CookieManager.getInstance().flush(); super.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); active = true;
        TvUi.immersive(this);
        if(library!=null&&engine!=null&&!"results".equals(collection)){int previousIndex=selectedResultIndex;showCollection(collection);selectedResultIndex=previousIndex;}
        else if(engine!=null&&!searchRunning&&results.length()>0)render();
        if(television&&grid!=null&&grid.getChildCount()>0&&verificationPanel==null)
            grid.post(()->{if(grid.getChildCount()>0)grid.getChildAt(Math.min(selectedResultIndex,grid.getChildCount()-1)).requestFocus();});
        if(google!=null){google.resume();return;}
        if (engine != null) {
            engine.onResume();
            if (searchRunning && verificationPanel == null) armSearchTimeout();
            if ("results".equals(collection)&&searchRunning&&results.length() == 0 && engine.getUrl() != null) inspect(++scanToken, 0);
        }
    }
    @Override protected void onDestroy() {
        active = false; handler.removeCallbacksAndMessages(null); thumbnails.close();
        if(google!=null){google.dispose();google=null;}
        dismissVerification();
        android.view.ViewGroup owner = (android.view.ViewGroup) engine.getParent();
        if (owner != null) owner.removeView(engine);
        engine.destroy(); super.onDestroy();
    }
    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color); return text;
    }
    private void addButton(LinearLayout row, String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setTextSize(television?16:12);
        TvUi.focusable(button);
        if(television)button.setPadding(dp(12),0,dp(12),0);
        button.setOnClickListener(v -> action.run()); row.addView(button);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

