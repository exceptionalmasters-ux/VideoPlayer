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
}
