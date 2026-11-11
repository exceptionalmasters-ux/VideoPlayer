package pl.example.viderplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public final class CollectionPageTest {
    @Test public void fullHistoryIsReachableWithBoundedGrid(){int seen=0;for(int start=0;start<200;start+=24){CollectionPage p=new CollectionPage(200,start,24);assertEquals(start,p.first);assertTrue(p.end-p.first<=24);seen+=p.end-p.first;}assertEquals(200,seen);}
    @Test public void selectionAndPageSurviveReturningFromMovie(){CollectionPage p=new CollectionPage(200,124,24);assertEquals(120,p.first);assertEquals(4,p.localSelection());assertEquals(144,p.move(1));assertEquals(96,p.move(-1));}
    @Test public void deletionAndEmptyListsClampSelection(){CollectionPage p=new CollectionPage(192,192,24);assertEquals(191,p.selected);assertEquals(168,p.first);assertEquals(0,new CollectionPage(0,-1,24).localSelection());assertEquals(0,new CollectionPage(1,0,24).move(1));}
}
