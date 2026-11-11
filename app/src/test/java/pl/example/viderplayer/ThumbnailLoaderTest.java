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

    @Test public void posterDecodeUsesPowerOfTwoSamplingWithinBounds(){
        for(int[] size:new int[][]{{5000,5000},{1920,1080},{481,321},{480,320},{20000,20}}){int sample=ThumbnailLoader.sampleSize(size[0],size[1]);assertEquals(0,sample&(sample-1));assertTrue(size[0]/sample<=480);assertTrue(size[1]/sample<=320);}
    }
}

