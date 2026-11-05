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
        if (message == null || message.length() > 4096 || !UrlPolicy.mediaOrigin(frameUrl)) return;
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
    public synchronized void adObservation(String message,String frameUrl){
        if(message==null||message.length()>4096||!UrlPolicy.mediaOrigin(frameUrl))return;
        try{
            JSONObject input=new JSONObject(message);String event=input.optString("event");
            if(!event.equals("ad-layer")&&!event.equals("ad-player"))return;
            JSONObject item=new JSONObject().put("elapsedMs",android.os.SystemClock.elapsedRealtime()-started).put("event",event.replace('-','.')).put("frame",UrlPolicy.redact(frameUrl));
            if(event.equals("ad-layer")){
                String kind=input.optString("kind");
                if(!java.util.Arrays.asList("freedisc-panel-1","freedisc-panel-2","adblock-message","background-wait","jw-vast").contains(kind))return;
                item.put("kind",kind);
                for(String key:new String[]{"present","visible","coversVideo"})item.put(key,input.optBoolean(key));
                for(String key:new String[]{"width","height"})item.put(key,Math.max(0,Math.min(16384,input.optInt(key))));
            }else{
                String signal=input.optString("signal"),state=input.optString("state");
                if(!java.util.Arrays.asList("snapshot","adStarted","adComplete","adSkipped","adError").contains(signal))return;
                item.put("signal",signal).put("state",java.util.Arrays.asList("idle","buffering","paused","playing","complete","error").contains(state)?state:"unknown");
                item.put("errorCode",Math.max(0,Math.min(999999,input.optInt("errorCode"))));
            }
            item.put("videoPresent",input.optBoolean("videoPresent")).put("sourceAssigned",input.optBoolean("sourceAssigned")).put("paused",input.optBoolean("paused",true));
            item.put("readyState",Math.max(0,Math.min(4,input.optInt("readyState")))).put("networkState",Math.max(0,Math.min(3,input.optInt("networkState"))));
            if(entries.size()>=500)entries.removeFirst();entries.addLast(item);
        }catch(JSONException ignored){}
    }
    public synchronized String export() {
        try {
            JSONObject report = new JSONObject();
            report.put("schemaVersion", 2);
            report.put("exportElapsedMs", android.os.SystemClock.elapsedRealtime() - started);
            report.put("appVersion", BuildConfig.VERSION_NAME);
            report.put("androidApi", android.os.Build.VERSION.SDK_INT);
            android.content.pm.PackageInfo pkg = android.webkit.WebView.getCurrentWebViewPackage();
            report.put("webViewVersion", pkg == null ? "unknown" : pkg.versionName);
            report.put("cookiesThirdParty", true);
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

