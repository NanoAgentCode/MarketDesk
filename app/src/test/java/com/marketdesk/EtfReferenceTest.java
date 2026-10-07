package com.marketdesk;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class EtfReferenceTest {
 private String raw(String extra){return "{\"rc\":0,\"data\":{\"diff\":[{\"f12\":\"561380\",\"f13\":1,\"f2\":0.672,\"f124\":1790755902,"+extra+"}]}}";}
 @Test public void iopvAndPriceAreSeparate() throws Exception {JSONObject q=EtfReference.parse(raw("\"f441\":0.6725"),"1.561380");assertEquals(0.6725,q.getDouble("value"),0);assertEquals(0.672,q.getDouble("quotePrice"),0);assertEquals(-0.074349,q.getDouble("premium"),0.00001);assertEquals(1790755902000L,q.getLong("packageTime"));}
 @Test public void yahooAndEastmoneyIdentifySameEtf(){assertEquals("1.518880",EtfReference.secid("Y:518880.SS"));assertEquals("1.561380",EtfReference.secid("E:1.561380"));assertEquals("0.159326",EtfReference.secid("Y:159326.SZ"));}
 @Test public void stockAndIndexAreNotEtfs(){assertEquals("",EtfReference.secid("E:1.600519"));assertEquals("",EtfReference.secid("Y:^NDX"));assertEquals("",EtfReference.secid("E:2.931250"));}
 @Test(expected=Exception.class) public void estimatedNavCannotMasqueradeAsIopv() throws Exception {EtfReference.parse(raw("\"GSZ\":0.6725,\"f441\":null"),"1.561380");}
 @Test(expected=Exception.class) public void noIopvCannotUseTradingPrice() throws Exception {EtfReference.parse(raw("\"f441\":\"-\""),"1.561380");}
 @Test(expected=Exception.class) public void wrongEtfIsRejected() throws Exception {EtfReference.parse(raw("\"f441\":0.6725"),"1.518880");}
}
