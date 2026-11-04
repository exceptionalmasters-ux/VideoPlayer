package pl.example.viderplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public final class PresentationPolicyTest {
 @Test public void selectedMovieEntersOnlyOnceUntilANewDocument(){
  PresentationPolicy p=new PresentationPolicy();assertTrue(p.shouldEnter());
  p.automaticallyEntered();assertFalse(p.shouldEnter());p.navigation();assertTrue(p.shouldEnter());
 }
 @Test public void backAndManualPageViewPreventAutomaticReentry(){
  PresentationPolicy p=new PresentationPolicy();p.automaticallyEntered();p.userControlled();
  assertFalse(p.shouldEnter());p.accessBlocked();assertFalse(p.shouldEnter());
  p.navigation();assertTrue(p.shouldEnter());
 }
 @Test public void verificationCanInterruptAndThenResumeAutomaticPresentation(){
  PresentationPolicy p=new PresentationPolicy();p.accessBlocked();assertTrue(p.shouldEnter());
  p.automaticallyEntered();p.accessBlocked();assertTrue(p.shouldEnter());
  p.automaticallyEntered();assertFalse(p.shouldEnter());
 }
}
