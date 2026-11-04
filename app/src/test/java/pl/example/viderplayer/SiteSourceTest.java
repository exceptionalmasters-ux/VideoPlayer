package pl.example.viderplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public final class SiteSourceTest {
    @Test public void searchesEncodeTitlesAsOnePathComponent() {
        assertEquals("https://freedisc.pl/search/all/Ronin%201998%2FPL%3F", SiteSource.FREEDISC.searchUrl("Ronin 1998/PL?"));
        assertEquals("https://vider.info/search/all/Maverick", SiteSource.VIDER.searchUrl("Maverick"));
    }
    @Test public void sourcesRestoreAndRejectUnrelatedHosts() {
        assertEquals(SiteSource.FREEDISC, SiteSource.restore("FREEDISC"));
        assertEquals(SiteSource.VIDER, SiteSource.restore(null));
        assertEquals(SiteSource.VIDER, SiteSource.restore("unknown"));
        assertEquals(SiteSource.FREEDISC, SiteSource.fromUrl("https://WWW.FREEDISC.PL/"));
        assertNull(SiteSource.fromUrl("https://freedisc.pl.evil.test/"));
        assertTrue(UrlPolicy.sameService("https://vider.info/", "https://vider.pl/"));
        assertFalse(UrlPolicy.sameService("https://vider.info/", "https://freedisc.pl/"));
    }

    @Test public void freediscUsesDesktopVersionAndViderKeepsOriginalBrowserIdentity() {
        String original = "Mozilla/5.0 (Linux; Android 14; device; wv) AppleWebKit/537.36 Version/4.0 Chrome/156.0.8078.47 Mobile Safari/537.36";
        assertEquals(original, SiteSource.VIDER.userAgent(original));
        String desktop = SiteSource.FREEDISC.userAgent(original);
        assertFalse(desktop.contains("Android"));
        assertFalse(desktop.contains("Mobile"));
        assertTrue(desktop.contains("Chrome/156.0.8078.47"));
        assertTrue(SiteSource.isMobileFreediscUrl("http://m.freedisc.pl/"));
        assertTrue(SiteSource.isMobileFreediscUrl("https://m.freedisc.pl/search/all/Ronin"));
        assertFalse(SiteSource.isMobileFreediscUrl("http://m.freedisc.pl.evil.test/"));
        assertFalse(SiteSource.isMobileFreediscUrl("http://user:pass@m.freedisc.pl/"));
        assertFalse(SiteSource.isMobileFreediscUrl("http://m.freedisc.pl:444/"));
        assertFalse(SiteSource.isMobileFreediscUrl("intent://m.freedisc.pl/"));
        assertFalse(UrlPolicy.allowed("http://m.freedisc.pl/"));
        assertEquals("freedisc_mobile", UrlPolicy.pageKind("http://m.freedisc.pl/"));
        assertEquals("insecure_http", UrlPolicy.pageKind("http://example.org/"));
    }
}
