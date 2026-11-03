package pl.example.viderplayer;
import org.jsoup.Jsoup;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ThumbnailLoaderTest {
    @Test public void readsTheMoviePosterFromItsOwnMetadata() {
        assertEquals("https://img.vider.info/i/example.png", ThumbnailLoader.poster(Jsoup.parse(
            "<meta property='og:image' content='https://img.vider.info/i/example.png'>", "https://vider.info/vid/+fexample")));
    }
    @Test public void readsEmbeddedPlayerImageAndResolvesRelativePoster() {
        assertEquals("https://img.vider.info/i/example.png", ThumbnailLoader.poster(Jsoup.parse(
            "<link rel='image_src' href='https://img.vider.info/i/example.png'>", "https://vider.pl/embed/video/example")));
        assertEquals("https://vider.pl/poster.png", ThumbnailLoader.poster(Jsoup.parse(
            "<video poster='/poster.png'></video>", "https://vider.pl/embed/video/example")));
    }
    @Test public void neverUsesAnUnrelatedFilmThumbnailAsThisFilmsPoster() {
        assertEquals("", ThumbnailLoader.poster(Jsoup.parse(
            "<aside><img src='https://img.vider.info/i/unrelated.png'></aside>", "https://vider.info/vid/+fexample")));
    }
}
