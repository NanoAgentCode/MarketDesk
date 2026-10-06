package com.marketdesk;
import org.junit.Test;
import static org.junit.Assert.*;
public class PinFeedbackTest {
 @Test public void launcherAcceptingIsNotSuccess(){assertEquals(PinFeedback.State.WAITING,PinFeedback.resolve(true,false,100));}
 @Test public void silenceDoesNotMeanRejected(){assertEquals(PinFeedback.State.UNCONFIRMED,PinFeedback.resolve(true,false,10000));}
 @Test public void declinedRequestShowsFallback(){assertEquals(PinFeedback.State.REJECTED,PinFeedback.resolve(false,false,0));}
 @Test public void lateConfirmationWins(){assertEquals(PinFeedback.State.CONFIRMED,PinFeedback.resolve(true,true,60000));}
 @Test public void existingWidgetIsNotConfirmation(){assertFalse(PinFeedback.hasNewWidget(new int[]{4},new int[]{4}));}
 @Test public void detectsNewIdEvenWhenOldWidgetRemoved(){assertTrue(PinFeedback.hasNewWidget(new int[]{4},new int[]{9}));}
}
