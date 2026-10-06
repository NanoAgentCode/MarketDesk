package com.marketdesk;
import org.json.*;
import org.junit.Test;
import java.util.*;
import java.time.Instant;
import static org.junit.Assert.*;
public class QuoteVerificationTest {
 private JSONObject q(String id,double price,double change,long time) throws Exception {return new JSONObject().put("origin",id).put("price",price).put("change",change).put("dataTime",time).put("unit","CNY").put("received",time);}
 private JSONObject combine(JSONObject... quotes) throws Exception{return QuoteVerification.combine(Arrays.asList(quotes),new JSONArray(),1000000);}
 @Test public void roundingDifferencesCanAgree() throws Exception {assertEquals("agree",combine(q("yahoo",8.633,1.338,900000),q("tencent",8.633,1.34,900000)).getString("verification"));}
 @Test public void materialPriceDifferenceIsVisible() throws Exception {assertEquals("different",combine(q("yahoo",100,1,900000),q("tencent",102,1,900000)).getString("verification"));}
 @Test public void previousCloseDifferenceIsVisible() throws Exception {assertEquals("different",combine(q("yahoo",100,1,900000),q("tencent",100,1.2,900000)).getString("verification"));}
 @Test public void delayedSourceIsNotDeclaredWrong() throws Exception {assertEquals("unaligned",combine(q("yahoo",100,1,900000),q("tencent",102,1,700000)).getString("verification"));}
 @Test public void sameProviderTwiceCannotBecomeDoubleVerification() throws Exception {assertEquals("single",combine(q("yahoo",100,1,900000),q("yahoo",100,1,900000)).getString("verification"));}
 @Test public void currencyMismatchIsNotCompared() throws Exception {JSONObject usd=q("tencent",100,1,900000).put("unit","USD");assertEquals("unaligned",combine(q("yahoo",100,1,900000),usd).getString("verification"));}
 @Test public void newestUsableDataIsSelectedWithoutAveraging() throws Exception {JSONObject result=combine(q("yahoo",100,1,800000),q("tencent",102,1,900000));assertEquals(102,result.getDouble("price"),0);assertEquals("different",result.getString("verification"));}
 @Test public void historicalCloseCanAgreeWithinSameDay() throws Exception {long day=Instant.parse("2026-09-30T00:00:00Z").toEpochMilli();JSONObject result=QuoteVerification.combine(Arrays.asList(q("yahoo",8.633,1.338,day+7*3600000),q("tencent",8.633,1.34,day+8*3600000)),new JSONArray(),day+6*86400000L);assertEquals("agree",result.getString("verification"));}
 @Test public void differentHistoricalDaysAreNotCompared() throws Exception {long day=Instant.parse("2026-09-30T00:00:00Z").toEpochMilli();JSONObject result=QuoteVerification.combine(Arrays.asList(q("yahoo",100,1,day),q("tencent",100,1,day-86400000)),new JSONArray(),day+6*86400000L);assertEquals("unaligned",result.getString("verification"));}
 @Test public void unavailableSourcesAreRetainedInDetails() throws Exception {JSONArray errors=new JSONArray().put(new JSONObject().put("source","eastmoney").put("error","网络超时"));JSONObject result=QuoteVerification.combine(Collections.singletonList(q("yahoo",100,1,900000)),errors,1000000);assertEquals("single",result.getString("verification"));assertEquals(1,result.getJSONArray("verificationFailures").length());}
 @Test(expected=Exception.class) public void missingAllPricesIsNotAgreement() throws Exception {combine(new JSONObject().put("origin","yahoo"));}
}
