package pl.example.viderplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public final class TvKeysTest {
 @Test public void arrowsAndCenterControlFullscreenButBackStaysNative(){
  assertEquals("back",TvKeys.fullscreen(21));assertEquals("forward",TvKeys.fullscreen(22));
  assertEquals("toggle",TvKeys.fullscreen(23));assertEquals("controls",TvKeys.fullscreen(19));
  assertEquals("controls",TvKeys.fullscreen(20));assertNull(TvKeys.fullscreen(4));
 }
 @Test public void mediaButtonsAreSeparateFromNormalMenuArrows(){
  assertEquals("toggle",TvKeys.media(85));assertEquals("play",TvKeys.media(126));
  assertEquals("pause",TvKeys.media(127));assertEquals("back",TvKeys.media(89));
  assertEquals("forward",TvKeys.media(90));assertNull(TvKeys.media(21));assertNull(TvKeys.media(23));
 }
}
