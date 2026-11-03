package pl.example.viderplayer;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Shared navigation and diagnostic redaction policy; never export URL credentials or queries. */
public final class UrlPolicy {
    private static final Set<String> HOSTS = new java.util.HashSet<>(java.util.Arrays.asList("vider.pl", "www.vider.pl", "vider.info", "www.vider.info"));
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
        try { return URI.create(value).getPath().matches("/vid/[^/]*\\+f[A-Za-z0-9]+/?"); }
        catch (RuntimeException e) { return false; }
    }
    public static boolean isHome(String value) {
        if (!allowed(value)) return false;
        String path = URI.create(value).getPath();
        return path == null || path.isEmpty() || path.equals("/");
    }
    public static boolean searchPage(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443) && host != null
                && (host.equals("www.google.com") || host.equals("google.com")
                    || host.equals("www.google.pl") || host.equals("google.pl")
                    || host.equals("consent.google.com") || host.equals("consent.google.pl"));
        } catch (RuntimeException e) { return false; }
    }
    public static boolean thumbnail(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443) && host != null
                && (host.equals("img.vider.info") || host.equals("gstatic.com") || host.endsWith(".gstatic.com"));
        } catch (RuntimeException e) { return false; }
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
            return "https://" + host + (knownHost && publicPath ? path : "/[path-omitted]");
        } catch (RuntimeException e) { return "[omitted]"; }
    }
}
