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
    private int scanToken;
    private boolean active = true;
    private final DiagnosticLog diagnostics = new DiagnosticLog();
    private static final int EXPORT_REPORT = 43;
    private FrameLayout engineHost;
    private FrameLayout screen;
    private LinearLayout searchContent;
    private LinearLayout verificationPanel;
    private String requestedSearch;
    private boolean searchReturnUsed;
    private long lastSearchAt;
    private int lastHttpError;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 24, 32));
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
        TextView brand = text("VideoPlayer", 26, Color.WHITE);
        brand.setTypeface(null, Typeface.BOLD);
        brand.setPadding(dp(16), dp(18), dp(16), dp(2));
        root.addView(brand);
        TextView caption = text("Znajdź film i dotknij miniatury", 14, Color.LTGRAY);
        caption.setPadding(dp(16), 0, dp(16), dp(14));
        root.addView(caption);
        LinearLayout input = new LinearLayout(this);
        input.setPadding(dp(12), 0, dp(12), 0);
        query = new EditText(this);
        query.setSingleLine(true);
        query.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        query.setHint("Tytuł filmu, np. Maverick");
        query.setTextSize(16);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.addView(query, new LinearLayout.LayoutParams(0, dp(52), 1));
        addButton(input, "Szukaj", this::search);
        root.addView(input);
        LinearLayout actions = new LinearLayout(this);
        addButton(actions, "Mam link", this::pasteLink);
        addButton(actions, "Raport", this::exportReport);
        root.addView(actions);
        status = text("Wpisz tytuł. Filmy pokażą się tutaj jako miniatury.", 13, Color.LTGRAY);
        status.setPadding(dp(16), dp(8), dp(16), dp(10));
        root.addView(status);
        FrameLayout workspace = new FrameLayout(this);
        root.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1));
        engine = new WebView(this);
        engineHost = new FrameLayout(this);
        workspace.addView(engineHost, new FrameLayout.LayoutParams(-1, -1));
        engineHost.addView(engine, new FrameLayout.LayoutParams(-1, -1));
        engine.setVisibility(View.INVISIBLE);
        engine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        WebSettings settings = engine.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(engine, true);
        resultsView = new ScrollView(this);
        grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(dp(8), 0, dp(8), dp(16));
        resultsView.addView(grid);
        workspace.addView(resultsView, new FrameLayout.LayoutParams(-1, -1));
        engine.setWebChromeClient(new WebChromeClient());
        engine.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return !"https".equals(request.getUrl().getScheme());
                String target = request.getUrl().toString();
                // A background navigation can never open a movie or launch another app.
                return !UrlPolicy.allowed(target);
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                diagnostics.add("search.page", url, 0);
                if (UrlPolicy.allowed(url)) inspect(++scanToken, 0);
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame()) {
                    lastHttpError = response.getStatusCode();
                    diagnostics.add("search.http_error", request.getUrl().toString(), lastHttpError);
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    diagnostics.add("search.network_error", request.getUrl().toString(), error.getErrorCode());
                    status.setText("Nie udało się połączyć. Spróbuj ponownie; raport pomoże ustalić przyczynę.");
                }
            }
        });
        query.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) { search(); return true; }
            return false;
        });
        if (saved != null) {
            query.setText(saved.getString("query", ""));
            try { results = new JSONArray(saved.getString("results", "[]")); } catch (Exception ignored) { }
            requestedSearch = saved.getString("requestedSearch");
            searchReturnUsed = saved.getBoolean("searchReturnUsed");
            engine.restoreState(saved);
            if (results.length() > 0) render();
            else if (!query.getText().toString().trim().isEmpty()) search();
        }
    }
    private void search() {
        String title = query.getText().toString().trim();
        if (title.isEmpty()) { query.setError("Wpisz tytuł filmu"); return; }
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastSearchAt != 0 && now - lastSearchAt < 2000) {
            status.setText("Poczekaj chwilę przed kolejnym wyszukiwaniem."); return;
        }
        lastSearchAt = now;
        lastHttpError = 0;
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(), 0);
        query.clearFocus();
        results = new JSONArray();
        thumbnails.nextGeneration();
        grid.removeAllViews();
        resultsView.setVisibility(View.VISIBLE);
        engine.setVisibility(View.INVISIBLE);
        status.setText("Szukam filmów…");
        ++scanToken;
        // Advertised by the site's public /opensearch.xml, not a guessed endpoint.
        requestedSearch = "https://vider.info/search/all/" + Uri.encode(title);
        searchReturnUsed = false;
        diagnostics.clear();
        diagnostics.add("search.start", requestedSearch, 0);
        engine.loadUrl(requestedSearch);
    }
    private void inspect(int token, int attempt) {
        if (!active || token != scanToken || !UrlPolicy.allowed(engine.getUrl())) return;
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
                showVerification(stage);
                if (attempt < 120) handler.postDelayed(() -> inspect(token, attempt + 1), 1500);
                return;
            }
            if (UrlPolicy.isHome(engine.getUrl()) && requestedSearch != null) {
                if (!searchReturnUsed) {
                    searchReturnUsed = true;
                    CookieManager.getInstance().flush();
                    diagnostics.add("search.return_after_home", requestedSearch, 0);
                    engine.loadUrl(requestedSearch);
                } else status.setText("Serwis przekierował wyszukiwanie na stronę główną. Spróbuj ponownie lub wyślij raport.");
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
                        if (item == null || !UrlPolicy.isMovie(item.optString("url"))) continue;
                        String path = Uri.parse(item.optString("url")).getPath();
                        String key = path.substring(path.lastIndexOf('+'));
                        if (!seen.add(key)) continue;
                        String name = item.optString("title").replaceAll("[\\p{Cntrl}]", " ").trim();
                        if (name.isEmpty()) continue;
                        item.put("title", name.substring(0, Math.min(180, name.length())));
                        accepted.put(item);
                    }
                    if (accepted.length() > 0) {
                        diagnostics.add("search.cards", null, accepted.length());
                        results = accepted; render(); return;
                    }
                }
            } catch (Exception ignored) { diagnostics.add("search.parse_error", null, 0); }
            if (attempt < 12) {
                handler.postDelayed(() -> inspect(token, attempt + 1), 1500);
            } else {
                dismissVerification();
                diagnostics.add("search.empty_or_unrecognized", engine.getUrl(), 0);
                status.setText(lastHttpError >= 400
                    ? "Serwis odpowiedział błędem HTTP " + lastHttpError + ". Wyślij raport wyszukiwania, jeśli problem się powtarza."
                    : "Brak odczytanych filmów. Spróbuj krótszego tytułu lub wyślij raport.");
            }
        });
    }
    private void showVerification(String stage) {
        if (verificationPanel != null || !active) return;
        android.view.ViewGroup parent = (android.view.ViewGroup) engine.getParent();
        if (parent != null) parent.removeView(engine);
        searchContent.setVisibility(View.GONE);
        verificationPanel = new LinearLayout(this);
        verificationPanel.setOrientation(LinearLayout.VERTICAL);
        verificationPanel.setBackgroundColor(Color.rgb(17, 24, 32));
        TextView heading = text("Potwierdzenie wyszukiwania", 20, Color.WHITE);
        heading.setPadding(dp(16), dp(12), dp(16), dp(4));
        verificationPanel.addView(heading);
        TextView explanation = text("verification".equals(stage)
            ? "Vider prosi o kod już przy wyszukiwaniu. Wpisz go poniżej i użyj przycisku na stronie."
            : "Dokończ wymagany przez Vider krok. Potem wrócimy do miniatur.", 13, Color.LTGRAY);
        explanation.setPadding(dp(16), 0, dp(16), dp(8));
        verificationPanel.addView(explanation);
        LinearLayout actions = new LinearLayout(this);
        addButton(actions, "Klawiatura", this::showVerificationKeyboard);
        addButton(actions, "Sprawdź", () -> inspect(++scanToken, 0));
        addButton(actions, "Anuluj", this::cancelVerification);
        verificationPanel.addView(actions);
        verificationPanel.addView(engine, new LinearLayout.LayoutParams(-1, 0, 1));
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
        engine.requestFocus();
        diagnostics.add("search.verification_open", null, 0);
        if ("verification".equals(stage)) engine.postDelayed(this::showVerificationKeyboard, 250);
    }
    private void showVerificationKeyboard() {
        if (!active || verificationPanel == null || !UrlPolicy.allowed(engine.getUrl())) return;
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
    private void cancelVerification() {
        ++scanToken;
        diagnostics.add("search.verification_cancelled", null, 0);
        dismissVerification();
        status.setText("Weryfikacja przerwana. Możesz ponowić wyszukiwanie.");
    }
    private void dismissVerification() {
        if (verificationPanel == null) return;
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(engine.getWindowToken(), 0);
        android.view.ViewGroup owner = (android.view.ViewGroup) engine.getParent();
        if (owner != null) owner.removeView(engine);
        screen.removeView(verificationPanel);
        verificationPanel = null;
        engine.clearFocus();
        engineHost.addView(engine, new FrameLayout.LayoutParams(-1, -1));
        engine.setVisibility(View.INVISIBLE);
        engine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        searchContent.setVisibility(View.VISIBLE);
        CookieManager.getInstance().flush();
    }
    @Override public void onBackPressed() {
        if (verificationPanel != null) cancelVerification();
        else super.onBackPressed();
    }
    private void render() {
        thumbnails.nextGeneration();
        dismissVerification();
        engine.setVisibility(View.INVISIBLE);
        resultsView.setVisibility(View.VISIBLE);
        grid.removeAllViews();
        status.setText("Znalezione filmy · " + results.length() + " · dotknij miniatury, aby otworzyć film");
        int width = (getResources().getDisplayMetrics().widthPixels - dp(40)) / 2;
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
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = width;
            params.height = dp(204);
            params.setMargins(dp(6), dp(6), dp(6), dp(6));
            grid.addView(card, params);
            FrameLayout art = new FrameLayout(this);
            card.addView(art, new LinearLayout.LayoutParams(-1, dp(116)));
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(36, 61, 72));
            image.setContentDescription(result.optString("title"));
            art.addView(image, new FrameLayout.LayoutParams(-1, -1));
            TextView play = text("▶", 32, Color.WHITE);
            play.setGravity(Gravity.CENTER);
            art.addView(play, new FrameLayout.LayoutParams(-1, -1));
            TextView name = text(result.optString("title"), 14, Color.WHITE);
            name.setMaxLines(3);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setPadding(dp(10), dp(8), dp(10), dp(4));
            card.addView(name);
            TextView host = text(Uri.parse(url).getHost(), 11, Color.rgb(114, 220, 196));
            host.setPadding(dp(10), 0, dp(10), dp(8));
            card.addView(host);
            card.setContentDescription("Odtwórz " + result.optString("title"));
            card.setOnClickListener(v -> openMovie(url));
            thumbnails.load(image, url, result.optString("thumbnail"), engine.getSettings().getUserAgentString());
        }
    }
    private void openMovie(String url) {
        if (!UrlPolicy.isMovie(url)) return;
        CookieManager.getInstance().flush();
        startActivity(new Intent(this, MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(url)));
    }
    private void exportReport() {
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
        input.setHint("https://vider.info/vid/…");
        new AlertDialog.Builder(this).setTitle("Otwórz link filmu").setView(input)
            .setPositiveButton("Otwórz", (d, w) -> {
                String url = input.getText().toString().trim();
                if (UrlPolicy.isMovie(url)) openMovie(url);
                else Toast.makeText(this, "Podaj link HTTPS do strony filmu Vider.", Toast.LENGTH_LONG).show();
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
        out.putString("query", query.getText().toString());
        out.putString("requestedSearch", requestedSearch);
        out.putBoolean("searchReturnUsed", searchReturnUsed);
        out.putString("results", results.toString());
        engine.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override protected void onPause() {
        active = false; handler.removeCallbacksAndMessages(null);
        engine.onPause(); CookieManager.getInstance().flush(); super.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); active = true;
        if (engine != null) { engine.onResume(); if (results.length() == 0 && engine.getUrl() != null) inspect(++scanToken, 0); }
    }
    @Override protected void onDestroy() {
        active = false; handler.removeCallbacksAndMessages(null); thumbnails.close();
        dismissVerification();
        android.view.ViewGroup owner = (android.view.ViewGroup) engine.getParent();
        if (owner != null) owner.removeView(engine);
        engine.destroy(); super.onDestroy();
    }
    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color); return text;
    }
    private void addButton(LinearLayout row, String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setTextSize(12);
        button.setOnClickListener(v -> action.run()); row.addView(button);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
