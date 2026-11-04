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
    @Test public void progressSurvivesReopenAndMetadataWithoutReordering(){
        MovieLibrary l=new MovieLibrary();MovieLibrary.Item a=item("one","Ronin",1),b=item("two","Maverick",2);l.opened(a);l.toggle(a);l.opened(b);
        l.progress(a.url,1234,7200,false);assertEquals(b.key(),l.items("history").get(0).key());assertEquals(1,l.find(a.url).opened);
        l.opened(item("one","",3));l.metadata(item("one","Ronin updated",0));assertEquals(1234,l.find(a.url).position);assertEquals(1234,l.items("favorites").get(0).position);
        MovieLibrary restored=new MovieLibrary();restored.restore("history",l.items("history"));assertEquals(1234,restored.find(a.url).position);
    }
    @Test public void completionRestartInvalidReportsAndDeletion(){
        MovieLibrary l=new MovieLibrary();MovieLibrary.Item a=item("one","Ronin",1);l.opened(a);l.progress(a.url,500,7200,false);
        l.progress(a.url,-1,7200,false);l.progress(a.url,9000,7200,false);l.progress(a.url,500,90000,false);assertEquals(500,l.find(a.url).position);
        l.progress(a.url,7180,7200,false);assertTrue(l.find(a.url).completed);assertEquals(0,l.find(a.url).position);
        l.progress(a.url,0,7200,false);assertFalse(l.find(a.url).completed);
        l.progress(a.url,400,7200,true);assertTrue(l.find(a.url).completed);
        l.clear("history");l.progress(a.url,300,7200,false);assertNull(l.find(a.url));
        assertEquals("1:02:03",MovieLibrary.clock(3723));assertEquals("2:05",MovieLibrary.clock(125));
    }
}

