package com.marketdesk;
import org.junit.Test;
import static org.junit.Assert.*;
public class RefreshIntervalTest {
 @Test public void supportedIntervalsArePreserved(){for(int n:new int[]{15,30,60,120})assertEquals(n,RefreshInterval.normalize(n));}
 @Test public void badPreferenceCannotCreateTightLoop(){for(int n:new int[]{0,-1,1,5,Integer.MAX_VALUE})assertEquals(30,RefreshInterval.normalize(n));}
}
