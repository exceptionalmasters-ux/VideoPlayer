package pl.example.viderplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public final class GoogleSearchPolicyTest {
    @Test public void queryIsScopedEncodedAndBoundToOriginalTitle(){
        String url=GoogleSearchPolicy.url("Żółty serial S01E02 & PL",SiteSource.FREEDISC);
        assertTrue(url.startsWith("https://www.google.com/search?hl=pl&q="));assertTrue(url.contains("site%3Afreedisc.pl"));
        assertTrue(GoogleSearchPolicy.results(url,"Żółty serial S01E02 & PL",SiteSource.FREEDISC));
        assertFalse(GoogleSearchPolicy.results(url,"Inny film",SiteSource.FREEDISC));assertFalse(GoogleSearchPolicy.results(url,"Żółty serial S01E02 & PL",SiteSource.VIDER));
        assertTrue(GoogleSearchPolicy.query("Ronin",SiteSource.VIDER).contains("site:vider.info OR site:vider.pl"));
    }
    @Test public void googleIsNotAddedToTheMovieAllowlist(){
        assertTrue(GoogleSearchPolicy.allowed("https://www.google.com/sorry/index"));assertTrue(GoogleSearchPolicy.allowed("https://consent.google.com/m"));
        for(String url:new String[]{"https://google.com.evil.test/search","https://user:secret@google.com/search","http://google.com/search","https://www.google.com:444/search","https://accounts.google.com/","https://www.google.com/url?q=https://evil.test/","javascript:alert(1)"})assertFalse(url,GoogleSearchPolicy.allowed(url));
        assertFalse(UrlPolicy.allowed("https://www.google.com/search"));assertFalse(UrlPolicy.isMovie("https://www.google.com/search"));
    }
    @Test public void embedAndFileShareHistoryKeyAndKeepOnlyNeededMatchParameter(){
        String embed="https://freedisc.pl/embed/video/8307530?match=1&token=secret#fragment";
        assertTrue(UrlPolicy.isMovie(embed));assertEquals("freedisc:8307530",MovieLibrary.key(embed));
        assertEquals(MovieLibrary.key("https://freedisc.pl/user,f-8307530,film-mp4"),MovieLibrary.key(embed));
        MovieLibrary.Item item=new MovieLibrary.Item(embed,"Film","",1);assertEquals("https://freedisc.pl/embed/video/8307530?match=1",item.url);
        MovieLibrary library=new MovieLibrary();library.opened(item);library.progress(embed,500,7200,false);library.opened(new MovieLibrary.Item("https://freedisc.pl/u,f-8307530,film-mp4","Film","",2));
        assertEquals(1,library.items("history").size());assertEquals(500,library.find(embed).position);
        assertFalse(UrlPolicy.isMovie("https://freedisc.pl/embed/video/not-a-number"));assertFalse(UrlPolicy.isMovie("https://freedisc.pl.evil.test/embed/video/8307530"));
    }
}
