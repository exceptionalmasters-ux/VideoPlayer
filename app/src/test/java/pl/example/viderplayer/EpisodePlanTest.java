package pl.example.viderplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public final class EpisodePlanTest {
    @Test public void paddedShortAndXFormsShareTheSameNextEpisode(){
        for(String title:new String[]{"Serial S1E01","Serial.s01e01.1080p.WEB-DL","Serial - 1x01 - Pilot","Serial S01 E01"}){
            EpisodePlan p=EpisodePlan.parse(title);assertNotNull(title,p);assertEquals("Serial S01E02",p.next().query());assertEquals("Serial S02E01",p.nextSeason().query());
        }
        assertEquals("Mr Robot S02E11",EpisodePlan.parse("Mr.Robot.S02E10.PL").next().query());
    }
    @Test public void onlyExactSeriesSeasonAndEpisodeAreSuggested(){
        EpisodePlan next=EpisodePlan.parse("Żółty serial S01E01").next();
        assertTrue(next.matches("Zolty-serial-1x02-PL-mp4"));assertFalse(next.matches("Żółty serial S01E03"));assertFalse(next.matches("Żółty serial S02E02"));assertFalse(next.matches("Inny serial S01E02"));
    }
    @Test public void filmsSpecialsPacksAndMissingSeriesAreNotGuessed(){
        for(String title:new String[]{"Ronin 1998","Film 1920x1080","S01E01","Serial S00E01","Serial S01E00","Serial S01E01E02","Serial S01E01 S01E02"})assertNull(title,EpisodePlan.parse(title));
    }
    @Test public void prefetchRunsOnceAtThirtySecondsAndEndDoesNotMeanNearEnd(){
        EpisodeFlow f=new EpisodeFlow(EpisodePlan.parse("Serial S01E01"));assertNull(f.playback(60,100,false));assertFalse(f.ended);
        assertEquals("Serial S01E02",f.playback(70,100,false));assertFalse(f.ended);assertNull(f.playback(71,100,false));assertNull(f.playback(99,100,false));assertFalse(f.ended);
        f.playback(100,100,true);assertTrue(f.ended);
    }
    @Test public void onlySuccessfulNoMatchMovesToNextSeasonAndOnlyOnce(){
        EpisodeFlow f=new EpisodeFlow(EpisodePlan.parse("Serial S01E02"));f.playback(90,100,false);
        f.access();assertEquals(EpisodeFlow.Stage.ACCESS,f.stage);assertEquals("Serial S01E03",f.target.query());assertFalse(f.seasonFallback);
        f.failed();assertFalse(f.seasonFallback);assertEquals("Serial S01E03",f.retry());
        assertEquals("Serial S02E01",f.results(false));assertTrue(f.seasonFallback);assertNull(f.results(false));assertEquals(EpisodeFlow.Stage.EMPTY,f.stage);
    }
    @Test public void manualAccessCanResumeSameRequestAndCachedResultsSurviveReplay(){
        EpisodeFlow f=new EpisodeFlow(EpisodePlan.parse("Serial S01E01"));f.playback(70,100,false);f.access();f.playback(100,100,true);
        assertEquals(EpisodeFlow.Stage.ACCESS,f.stage);f.searching();f.results(true);assertEquals(EpisodeFlow.Stage.READY,f.stage);assertFalse(f.seasonFallback);
        f.dismissed=true;f.playback(0,100,false);assertFalse(f.ended);assertFalse(f.dismissed);assertEquals(EpisodeFlow.Stage.READY,f.stage);
    }
}
