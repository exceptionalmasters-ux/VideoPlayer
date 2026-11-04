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
    public static SiteSource restore(String name) {
        try { return valueOf(name); } catch (RuntimeException e) { return VIDER; }
    }
    public static SiteSource fromUrl(String value) {
        if (!UrlPolicy.allowed(value)) return null;
        String host = URI.create(value).getHost();
        return host.equalsIgnoreCase("freedisc.pl") || host.equalsIgnoreCase("www.freedisc.pl") ? FREEDISC : VIDER;
    }
}
