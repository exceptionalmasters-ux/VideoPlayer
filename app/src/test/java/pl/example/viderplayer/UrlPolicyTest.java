package pl.example.viderplayer;

import org.junit.Test;
import static org.junit.Assert.*;

public final class UrlPolicyTest {
    @Test public void allowsBothSiteDomainsAndExplicitTlsPort() {
        assertTrue(UrlPolicy.allowed("https://vider.info/vid/+example"));
        assertTrue(UrlPolicy.allowed("https://WWW.VIDER.PL:443/embed/video/example"));
    }
    @Test public void rejectsLookalikesAndNonTlsNavigation() {
        for (String value : new String[]{"https://vider.pl.evil.test/", "https://evil.test/?vider.pl",
                "https://vider.pl@evil.test/", "https://user:secret@vider.pl/", "https://vider.pl:444/",
                "http://vider.pl/", "javascript:alert(1)", "file:///data/file", "https://vider.pl\\@evil.test/", "broken"}) {
            assertFalse(value, UrlPolicy.allowed(value));
        }
        assertFalse(UrlPolicy.allowed(null));
    }
    @Test public void stripsAccountAndTokenParametersAndFragments() {
        assertEquals("https://stream.vider.info/video/123/v.mp4",
            UrlPolicy.redact("https://stream.vider.info/video/123/v.mp4?uid=private&token=secret#secret"));
    }
    @Test public void stripsCredentialsAndPrivatePaths() {
        assertEquals("https://vider.pl/[path-omitted]", UrlPolicy.redact("https://user:secret@vider.pl/private-profile/messages?token=secret"));
        assertEquals("https://ads.example/[path-omitted]", UrlPolicy.redact("https://ads.example/private/token?uid=secret"));
    }
    @Test public void onlyPreservesKnownPublicRoutes() {
        assertEquals("https://vider.pl/vid/+example", UrlPolicy.redact("https://vider.pl/vid/+example?uid=secret"));
        assertEquals("https://vider.pl/[path-omitted]", UrlPolicy.redact("https://vider.pl/vid/private-name+example"));
        assertEquals("[omitted]", UrlPolicy.redact("data:text/html,private"));
        assertEquals("[omitted]", UrlPolicy.redact(null));
    }
    @Test public void restrictsSearchNavigationAndThumbnailDownloads() {
        assertTrue(UrlPolicy.searchPage("https://www.google.com/search?q=test"));
        assertTrue(UrlPolicy.searchPage("https://consent.google.com/"));
        assertFalse(UrlPolicy.searchPage("https://google.com.evil.test/"));
        assertFalse(UrlPolicy.searchPage("https://user:secret@www.google.com/"));
        assertTrue(UrlPolicy.thumbnail("https://img.vider.info/i/example.png"));
        assertTrue(UrlPolicy.thumbnail("https://encrypted-tbn0.gstatic.com/image"));
        assertFalse(UrlPolicy.thumbnail("https://gstatic.com.evil.test/image"));
        assertFalse(UrlPolicy.thumbnail("https://stream.vider.info/video/123/v.mp4"));
        assertFalse(UrlPolicy.thumbnail("file:///storage/picture.png"));
    }
    @Test public void distinguishesMoviePagesFromHomeDirectoriesAndUnrelatedSites() {
        assertTrue(UrlPolicy.isMovie("https://vider.info/vid/+fexample"));
        assertTrue(UrlPolicy.isMovie("https://vider.pl/vid/movie-title+fexample"));
        assertFalse(UrlPolicy.isMovie("https://vider.pl/dir/+dexample"));
        assertFalse(UrlPolicy.isMovie("https://evil.test/vid/+fexample"));
        assertTrue(UrlPolicy.isHome("https://vider.pl"));
        assertTrue(UrlPolicy.isHome("https://vider.info/?ref=home"));
        assertFalse(UrlPolicy.isHome("https://vider.pl/vid/+fexample"));
    }
}
