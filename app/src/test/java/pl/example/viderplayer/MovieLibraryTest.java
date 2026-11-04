package pl.example.viderplayer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class MovieLibraryTest {
    private MovieLibrary.Item item(String id,String title,long time){return new MovieLibrary.Item("https://vider.info/vid/+f"+id,title,"",time);}
    @Test public void reopeningDeduplicatesDomainsAndKeepsMetadata(){MovieLibrary l=new MovieLibrary();l.opened(item("one","Ronin",1));l.opened(item("two","Maverick",2));l.opened(new MovieLibrary.Item("https://vider.pl/vid/+fone?tracking=secret","","",3));assertEquals(2,l.items("history").size());assertEquals("Ronin",l.items("history").get(0).title);assertFalse(l.items("history").get(0).url.contains("secret"));}
    @Test public void historyDeletionDoesNotRemoveFavoritesAndMetadataCannotResurrect(){MovieLibrary l=new MovieLibrary();MovieLibrary.Item i=item("one","Ronin",1);l.opened(i);l.toggle(i);l.clear("history");assertTrue(l.favorite(i.url));l.metadata(item("one","Ronin updated",2));assertEquals(0,l.items("history").size());assertEquals("Ronin updated",l.items("favorites").get(0).title);l.toggle(i);l.metadata(i);assertNull(l.find(i.url));}
    @Test public void historyIsBoundedAndRestoreKeepsOrder(){MovieLibrary l=new MovieLibrary();for(int i=0;i<205;i++)l.opened(item("n"+i,"Film "+i,i));assertEquals(200,l.items("history").size());MovieLibrary restored=new MovieLibrary();restored.restore("history",l.items("history"));assertEquals("Film 204",restored.items("history").get(0).title);restored.remove("history",item("n204","",0).url);assertEquals("Film 203",restored.items("history").get(0).title);}
    @Test public void freediscKeyIgnoresChangedSlugAndRejectsExternal(){assertEquals(MovieLibrary.key("https://freedisc.pl/user,f-123,ronin-mp4"),MovieLibrary.key("https://www.freedisc.pl/other,f-123,ronin"));assertEquals("",MovieLibrary.key("https://freedisc.pl.evil.test/user,f-123"));}
}
