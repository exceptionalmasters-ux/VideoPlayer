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
    @Test public void restrictsThumbnailDownloads() {
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

    @Test public void freediscFileLinksAndImagesAreScopedAndRedacted() {
        String movie = "https://freedisc.pl/margot223,f-19724546,ronin-1998-mp4";
        assertTrue(UrlPolicy.isMovie(movie));
        assertTrue(UrlPolicy.isMovie("https://www.freedisc.pl/user,f-123"));
        assertFalse(UrlPolicy.isMovie("https://freedisc.pl/user,d-123,folder"));
        assertFalse(UrlPolicy.isMovie("https://freedisc.pl.evil.test/user,f-123,movie-mp4"));
        assertFalse(UrlPolicy.isMovie("http://freedisc.pl/user,f-123,movie-mp4"));
        assertFalse(UrlPolicy.isMovie("https://user:secret@freedisc.pl/user,f-123"));
        assertEquals("https://freedisc.pl/file/19724546", UrlPolicy.redact(movie + "?token=secret#private"));
        assertTrue(UrlPolicy.thumbnail("https://img.freedisc.pl/photo/19724546/7/2/ronin.png"));
        assertFalse(UrlPolicy.thumbnail("https://img.freedisc.pl.evil.test/photo.png"));
        assertTrue(UrlPolicy.mediaOrigin("https://stream.freedisc.pl/embed/video/123"));
        assertFalse(UrlPolicy.mediaOrigin("https://stream.freedisc.pl:444/embed/video/123"));
        assertFalse(UrlPolicy.mediaOrigin("https://evil.freedisc.pl/"));
    }

    @Test public void classifiesUnusualPagesWithoutExportingTheirContents() {
        assertEquals("missing", UrlPolicy.pageKind(null));
        assertEquals("missing", UrlPolicy.pageKind(""));
        assertEquals("blank", UrlPolicy.pageKind("about:blank"));
        assertEquals("webview_error", UrlPolicy.pageKind("chrome-error://chromewebdata/"));
        assertEquals("supported", UrlPolicy.pageKind("https://freedisc.pl/search/all/private-title"));
        assertEquals("external_https", UrlPolicy.pageKind("https://example.org/private?token=secret"));
        assertEquals("other", UrlPolicy.pageKind("data:text/html,private"));
        assertEquals("other", UrlPolicy.pageKind("not a URL"));
        assertEquals("[omitted]", UrlPolicy.redact("chrome-error://chromewebdata/private"));
    }
}
