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

/** Native search screen. Google runs in its own WebView; movie pages use the player Activity. */
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

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 24, 32));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
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
        query.setHint("Tytuł filmu, np. Maverick");
        query.setTextSize(16);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.addView(query, new LinearLayout.LayoutParams(0, dp(52), 1));
        addButton(input, "Szukaj", this::search);
        root.addView(input);
        LinearLayout actions = new LinearLayout(this);
        addButton(actions, "Mam link", this::pasteLink);
        addButton(actions, "Strona wyników", this::showSource);
        root.addView(actions);
        status = text("Wpisz tytuł. Wyniki z Google będą pokazane tutaj jako kafelki.", 13, Color.LTGRAY);
        status.setPadding(dp(16), dp(8), dp(16), dp(10));
        root.addView(status);
        FrameLayout workspace = new FrameLayout(this);
        root.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1));
        engine = new WebView(this);
        workspace.addView(engine, new FrameLayout.LayoutParams(-1, -1));
        engine.setVisibility(View.INVISIBLE);
        WebSettings settings = engine.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(engine, false);
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
                if (UrlPolicy.isMovie(target)) { openMovie(target); return true; }
                return !UrlPolicy.searchPage(target);
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                if (UrlPolicy.searchPage(url)) inspect(++scanToken, 0);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    status.setText("Nie udało się połączyć z wyszukiwarką. Sprawdź internet i spróbuj ponownie.");
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
            engine.restoreState(saved);
            if (results.length() > 0) render();
            else if (!query.getText().toString().trim().isEmpty()) search();
        }
    }
    private void search() {
        String title = query.getText().toString().trim();
        if (title.isEmpty()) { query.setError("Wpisz tytuł filmu"); return; }
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(), 0);
        query.clearFocus();
        results = new JSONArray();
        thumbnails.nextGeneration();
        grid.removeAllViews();
        resultsView.setVisibility(View.VISIBLE);
        engine.setVisibility(View.INVISIBLE);
        status.setText("Szukam filmów…");
        ++scanToken;
        String expression = title + " (site:vider.info/vid/ OR site:vider.pl/vid/)";
        engine.loadUrl("https://www.google.com/search?hl=pl&q=" + Uri.encode(expression));
    }
    private void inspect(int token, int attempt) {
        if (!active || token != scanToken || !UrlPolicy.searchPage(engine.getUrl())) return;
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
                    if (accepted.length() > 0) { results = accepted; render(); return; }
                }
            } catch (Exception ignored) { }
            if (attempt >= 2) {
                engine.setVisibility(View.VISIBLE);
                resultsView.setVisibility(View.GONE);
                status.setText("Nie udało się jeszcze odczytać kafelków. Jeśli Google prosi o zgodę lub weryfikację, dokończ ją poniżej. Link filmu otworzy się od razu w aplikacji.");
            }
            if (attempt < 20) handler.postDelayed(() -> inspect(token, attempt + 1), 1500);
        });
    }
    private void render() {
        thumbnails.nextGeneration();
        engine.setVisibility(View.INVISIBLE);
        resultsView.setVisibility(View.VISIBLE);
        grid.removeAllViews();
        status.setText("Wyniki Google · " + results.length() + " · dotknij miniatury, aby otworzyć film");
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
    private void showSource() {
        if (engine.getUrl() == null) { search(); return; }
        boolean showing = engine.getVisibility() == View.VISIBLE;
        engine.setVisibility(showing ? View.INVISIBLE : View.VISIBLE);
        resultsView.setVisibility(showing ? View.VISIBLE : View.GONE);
        if (showing) inspect(++scanToken, 0);
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
        ((android.view.ViewGroup) engine.getParent()).removeView(engine); engine.destroy(); super.onDestroy();
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
