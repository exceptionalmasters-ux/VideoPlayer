package pl.example.viderplayer;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MovieNavigationTest {
    @Test public void waitsForConsentThenRetriesTheSelectedMovieOnlyOnce() {
        MovieNavigation navigation = new MovieNavigation();
        navigation.select("https://vider.info/vid/+fexample");
        assertNull(navigation.retryFrom("https://vider.pl/", true));
        assertEquals("https://vider.info/vid/+fexample", navigation.retryFrom("https://vider.pl/", false));
        assertNull(navigation.retryFrom("https://vider.pl/", false));
    }
    @Test public void neverRedirectsAnExistingMovieOrAnExternalPage() {
        MovieNavigation navigation = new MovieNavigation();
        navigation.select("https://vider.info/vid/+fexample");
        assertNull(navigation.retryFrom("https://vider.info/vid/+fexample", false));
        assertNull(navigation.retryFrom("https://example.org/", false));
        assertFalse(navigation.retryUsed());
    }
    @Test public void newSelectionAndRestoreKeepRetryBudgetCorrect() {
        MovieNavigation navigation = new MovieNavigation();
        navigation.restore("https://vider.pl/vid/+fexample", true);
        assertNull(navigation.retryFrom("https://vider.pl/", false));
        navigation.select("https://vider.info/vid/title+fother");
        assertEquals("https://vider.info/vid/title+fother", navigation.retryFrom("https://vider.info/", false));
        navigation.select("https://vider.info/");
        assertNull(navigation.target());
    }
}
