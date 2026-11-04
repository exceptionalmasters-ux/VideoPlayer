package pl.example.viderplayer;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Shared navigation and diagnostic redaction policy; never export URL credentials or queries. */
public final class UrlPolicy {
    private static final Set<String> HOSTS = new java.util.HashSet<>(java.util.Arrays.asList("vider.pl", "www.vider.pl", "vider.info", "www.vider.info", "freedisc.pl", "www.freedisc.pl"));
    private UrlPolicy() {}
    public static boolean allowed(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getHost() != null
                && HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) { return false; }
    }
    public static boolean isMovie(String value) {
        if (!allowed(value)) return false;
        try {
            String path = URI.create(value).getPath();
            if (SiteSource.fromUrl(value) == SiteSource.FREEDISC) {
                return path.matches("/[^/,]+,f-[0-9]+(?:,[^/]+)?/?");
            }
            return path.matches("/vid/[^/]*\\+f[A-Za-z0-9]+/?");
        }
        catch (RuntimeException e) { return false; }
    }
    public static boolean isHome(String value) {
        if (!allowed(value)) return false;
        String path = URI.create(value).getPath();
        return path == null || path.isEmpty() || path.equals("/");
    }
    public static boolean sameService(String left, String right) {
        SiteSource source = SiteSource.fromUrl(left);
        return source != null && source == SiteSource.fromUrl(right);
    }
    public static java.util.Set<String> mediaOrigins() {
        return new java.util.HashSet<>(java.util.Arrays.asList("https://vider.pl", "https://www.vider.pl",
            "https://vider.info", "https://www.vider.info", "https://freedisc.pl", "https://www.freedisc.pl",
            "https://stream.freedisc.pl", "https://stream2.freedisc.pl", "https://ovh.freedisc.pl"));
    }
    public static boolean mediaOrigin(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getHost() != null && "https".equalsIgnoreCase(uri.getScheme())
                && mediaOrigins().contains("https://" + uri.getHost().toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) { return false; }
    }
    public static boolean thumbnail(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443) && host != null
                && (host.equals("img.vider.info") || host.equals("img.freedisc.pl") || host.equals("gstatic.com") || host.endsWith(".gstatic.com"));
        } catch (RuntimeException e) { return false; }
    }
    /** Classifies a page without retaining arbitrary URL contents or search text. */
    public static String pageKind(String value) {
        if (value == null || value.isEmpty()) return "missing";
        if (allowed(value)) return "supported";
        if (value.equals("about:blank") || value.startsWith("about:blank#")) return "blank";
        if (value.startsWith("chrome-error://") || value.startsWith("chrome://network-error/")) return "webview_error";
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? "external_https" : "other";
        } catch (RuntimeException e) { return "other"; }
    }
    public static String redact(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) return "[omitted]";
            host = host.toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();
            boolean knownHost = HOSTS.contains(host) || host.equals("stream.vider.info");
            boolean publicPath = path != null && (path.equals("/")
                || path.matches("/vid/\\+[A-Za-z0-9]+")
                || path.matches("/embed/video/[A-Za-z0-9]+")
                || path.matches("/video/[0-9]+/v\\.mp4"));
            if ((host.equals("freedisc.pl") || host.equals("www.freedisc.pl")) && path != null) {
                java.util.regex.Matcher file = java.util.regex.Pattern.compile("/[^/,]+,f-([0-9]+)(?:,[^/]+)?/?").matcher(path);
                if (file.matches()) return "https://" + host + "/file/" + file.group(1);
            }
            return "https://" + host + (knownHost && publicPath ? path : "/[path-omitted]");
        } catch (RuntimeException e) { return "[omitted]"; }
    }
}
