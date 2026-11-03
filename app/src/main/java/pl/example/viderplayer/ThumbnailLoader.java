package pl.example.viderplayer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.CookieManager;
import android.widget.ImageView;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fetch only bounded HTML metadata and images. Never request a movie/stream as a thumbnail. */
final class ThumbnailLoader {
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final android.util.LruCache<String, Bitmap> cache = new android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    private volatile boolean stopped;
    private volatile int generation;
    void nextGeneration() { ++generation; ((java.util.concurrent.ThreadPoolExecutor) executor).getQueue().clear(); }
    void load(ImageView target, String movie, String supplied, String userAgent) {
        Bitmap cached = cache.get(movie);
        if (cached != null) { target.setImageBitmap(cached); return; }
        final int epoch = generation;
        executor.execute(() -> {
            if (epoch != generation || stopped) return;
            Bitmap result = null;
            try {
                result = image(supplied, userAgent);
                if (result == null && UrlPolicy.isMovie(movie)) {
                    byte[] html = request(movie, false, userAgent, 768 * 1024);
                    if (html != null) {
                        org.jsoup.nodes.Document doc = Jsoup.parse(new String(html, java.nio.charset.StandardCharsets.UTF_8), movie);
                        result = image(poster(doc), userAgent);
                        if (result == null) {
                            Element frame = doc.selectFirst(".video-frame iframe[src]");
                            String embed = frame == null ? "" : frame.attr("abs:src");
                            if (UrlPolicy.allowed(embed) && new java.net.URI(embed).getPath().startsWith("/embed/video/")) {
                                byte[] embeddedHtml = request(embed, false, userAgent, 768 * 1024, movie);
                                if (embeddedHtml != null) {
                                    org.jsoup.nodes.Document player = Jsoup.parse(new String(embeddedHtml,
                                        java.nio.charset.StandardCharsets.UTF_8), embed);
                                    result = image(poster(player), userAgent);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) { /* Keep the neutral play thumbnail on failure. */ }
            if (result != null) cache.put(movie, result);
            final Bitmap bitmap = result;
            if (!stopped && epoch == generation && bitmap != null) ui.post(() -> { if (!stopped && epoch == generation) target.setImageBitmap(bitmap); });
        });
    }
    static String poster(org.jsoup.nodes.Document doc) {
        Element meta = doc.selectFirst("meta[property=og:image], meta[name=og:image], link[rel=image_src]");
        if (meta != null) return meta.hasAttr("content") ? meta.attr("abs:content") : meta.attr("abs:href");
        Element video = doc.selectFirst("video[poster]");
        return video == null ? "" : video.attr("abs:poster");
    }
    private Bitmap image(String source, String userAgent) throws Exception {
        if (source == null || source.isEmpty()) return null;
        byte[] bytes;
        if (source.matches("(?s)^data:image/(png|jpeg|webp);base64,.*") && source.length() < 200000) {
            bytes = Base64.decode(source.substring(source.indexOf(',') + 1), Base64.DEFAULT);
        } else {
            if (!UrlPolicy.thumbnail(source)) return null;
            bytes = request(source, true, userAgent, 2 * 1024 * 1024);
        }
        if (bytes == null) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 1 || options.outHeight <= 1 || (long) options.outWidth * options.outHeight > 25000000L) return null;
        options.inJustDecodeBounds = false;
        options.inSampleSize = Math.max(1, Math.max(options.outWidth / 480, options.outHeight / 320));
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
    private byte[] request(String start, boolean image, String userAgent, int limit) throws Exception {
        return request(start, image, userAgent, limit, null);
    }
    private byte[] request(String start, boolean image, String userAgent, int limit, String referrer) throws Exception {
        String address = start;
        for (int hop = 0; hop < 4 && !stopped; hop++) {
            if (image ? !UrlPolicy.thumbnail(address) : !UrlPolicy.allowed(address)) return null;
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", userAgent);
                if (!image && UrlPolicy.allowed(referrer)) connection.setRequestProperty("Referer", referrer);
                // Credentials go only to the matching Vider origin. Never forward them to image hosts.
                if (!image) {
                    String cookie = CookieManager.getInstance().getCookie(address);
                    if (cookie != null) connection.setRequestProperty("Cookie", cookie);
                }
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String next = connection.getHeaderField("Location");
                    if (next == null) return null;
                    address = new URL(new URL(address), next).toString();
                    continue;
                }
                if (code != 200) return null;
                String type = connection.getContentType();
                if (type == null || (image ? !type.startsWith("image/") : !type.toLowerCase(java.util.Locale.ROOT).contains("text/html"))) return null;
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        if (out.size() + n > limit) return null;
                        out.write(buffer, 0, n);
                    }
                    return out.toByteArray();
                }
            } finally { connection.disconnect(); }
        }
        return null;
    }
    void close() { stopped = true; executor.shutdownNow(); ui.removeCallbacksAndMessages(null); }
}
