package pl.example.viderplayer;

import java.net.URI;
import java.net.URLEncoder;

/** Separate search/session scope for each supported service. */
public enum SiteSource {
    VIDER("Vider", "https://vider.info", "Maverick"),
    FREEDISC("Freedisc", "https://freedisc.pl", "Ronin");
    public final String label;
    public final String home;
    public final String example;
    SiteSource(String label, String home, String example) {
        this.label = label; this.home = home; this.example = example;
    }
    public String searchUrl(String title) {
        try { return home + "/search/all/" + URLEncoder.encode(title, "UTF-8").replace("+", "%20"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    public String userAgent(String original) {
        if (this != FREEDISC) return original;
        java.util.regex.Matcher chrome = java.util.regex.Pattern.compile("Chrome/([0-9.]+)").matcher(original == null ? "" : original);
        String version = chrome.find() ? chrome.group(1) : "120.0.0.0";
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
            + version + " Safari/537.36";
    }
    public static boolean isMobileFreediscUrl(String value) {
        try {
            URI uri = URI.create(value);
            boolean http = "http".equalsIgnoreCase(uri.getScheme());
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            return "m.freedisc.pl".equalsIgnoreCase(uri.getHost()) && uri.getRawUserInfo() == null
                && (http && (uri.getPort() == -1 || uri.getPort() == 80)
                    || https && (uri.getPort() == -1 || uri.getPort() == 443));
        } catch (RuntimeException e) { return false; }
    }
    public static SiteSource restore(String name) {
        try { return valueOf(name); } catch (RuntimeException e) { return VIDER; }
    }
    public static SiteSource fromUrl(String value) {
        if (!UrlPolicy.allowed(value)) return null;
        String host = URI.create(value).getHost();
        return host.equalsIgnoreCase("freedisc.pl") || host.equalsIgnoreCase("www.freedisc.pl") ? FREEDISC : VIDER;
    }
}
