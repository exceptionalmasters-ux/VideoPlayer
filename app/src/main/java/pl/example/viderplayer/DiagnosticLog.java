package pl.example.viderplayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.Set;

/** Bounded, in-memory, typed events. Raw headers, console text and HTML never enter this log. */
public final class DiagnosticLog {
    private final ArrayDeque<JSONObject> entries = new ArrayDeque<>();
    private final long started = android.os.SystemClock.elapsedRealtime();
    private static final Set<String> MEDIA_EVENTS = new java.util.HashSet<>(java.util.Arrays.asList("snapshot", "play", "playing", "pause", "waiting",
        "stalled", "error", "loadedmetadata", "ended", "seeking", "seeked"));
    public synchronized void add(String event, String url, int code) {
        try {
            JSONObject item = new JSONObject();
            item.put("elapsedMs", android.os.SystemClock.elapsedRealtime() - started);
            item.put("event", event);
            item.put("url", UrlPolicy.redact(url));
            item.put("code", code);
            if (entries.size() >= 500) entries.removeFirst();
            entries.addLast(item);
        } catch (JSONException ignored) { }
    }
    public synchronized void media(String message, String frameUrl) {
        if (message == null || message.length() > 4096 || !UrlPolicy.allowed(frameUrl)) return;
        try {
            JSONObject input = new JSONObject(message);
            String type = input.optString("event");
            if (!MEDIA_EVENTS.contains(type)) return;
            JSONObject item = new JSONObject();
            item.put("elapsedMs", android.os.SystemClock.elapsedRealtime() - started);
            item.put("event", "media." + type);
            item.put("frame", UrlPolicy.redact(frameUrl));
            item.put("source", UrlPolicy.redact(input.optString("source")));
            item.put("readyState", Math.max(0, Math.min(4, input.optInt("readyState"))));
            item.put("networkState", Math.max(0, Math.min(3, input.optInt("networkState"))));
            item.put("errorCode", Math.max(0, Math.min(4, input.optInt("errorCode"))));
            item.put("paused", input.optBoolean("paused", true));
            double time = input.optDouble("time", 0);
            item.put("time", Double.isFinite(time) ? Math.max(0, Math.min(864000, Math.floor(time))) : 0);
            if (entries.size() >= 500) entries.removeFirst();
            entries.addLast(item);
        } catch (JSONException ignored) { }
    }
    public synchronized String export() {
        try {
            JSONObject report = new JSONObject();
            report.put("schemaVersion", 1);
            report.put("appVersion", BuildConfig.VERSION_NAME);
            report.put("androidApi", android.os.Build.VERSION.SDK_INT);
            android.content.pm.PackageInfo pkg = android.webkit.WebView.getCurrentWebViewPackage();
            report.put("webViewVersion", pkg == null ? "unknown" : pkg.versionName);
            report.put("cookiesThirdParty", false);
            report.put("note", "No cookies, headers, page text, console messages or URL query parameters are recorded. Events from the page are untrusted observations.");
            JSONArray data = new JSONArray();
            for (JSONObject entry : entries) data.put(entry);
            report.put("events", data);
            return report.toString(2);
        } catch (JSONException e) { return "{}"; }
    }
    public synchronized void clear() { entries.clear(); }
    public synchronized int size() { return entries.size(); }
}
