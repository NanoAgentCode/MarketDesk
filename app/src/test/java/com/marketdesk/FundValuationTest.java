package com.marketdesk;
import org.junit.Test;
import org.json.JSONObject;
import java.time.Instant;
import static org.junit.Assert.*;
public class FundValuationTest {
 private String raw(String values){return "{\"success\":true,\"errorCode\":0,\"data\":[{\"FCODE\":\"000216\",\"NAV\":3.1359,\"PDATE\":\"2026-09-30\","+values+"}]}";}
 @Test public void estimateAndPublishedNavStaySeparate() throws Exception {JSONObject v=FundValuation.parse(raw("\"GSZ\":3.1368,\"GSZZL\":1.09,\"GZTIME\":\"2026-09-30 15:30\""),"000216");assertEquals(3.1359,v.getJSONObject("nav").getDouble("value"),0);assertEquals(3.1368,v.getJSONObject("estimate").getDouble("value"),0);JSONObject q=FundValuation.quote(v,1000);assertEquals("estimate",q.getString("kind"));assertEquals(1.09,q.getDouble("change"),0);}
 @Test public void noEstimateMeansOfficialNavNotInventedChange() throws Exception {JSONObject q=FundValuation.quote(FundValuation.parse(raw("\"GSZ\":null,\"GSZZL\":null,\"GZTIME\":null"),"000216"),1000);assertEquals("nav",q.getString("kind"));assertEquals(3.1359,q.getDouble("price"),0);assertFalse(q.has("change"));assertEquals("已公布净值",q.getString("unit"));}
 @Test public void missingTimestampCannotBecomeLiveEstimate() throws Exception {JSONObject v=FundValuation.parse(raw("\"GSZ\":3.5,\"GZTIME\":null"),"000216");assertFalse(v.has("estimate"));assertEquals("nav",FundValuation.quote(v,0).getString("kind"));}
 @Test public void zeroEstimateIsIgnored() throws Exception {JSONObject v=FundValuation.parse(raw("\"GSZ\":0,\"GZTIME\":\"2026-09-30 15:30\""),"000216");assertFalse(v.has("estimate"));}
 @Test public void estimateUsesBeijingTimezone() throws Exception {JSONObject v=FundValuation.parse(raw("\"GSZ\":3.1368,\"GZTIME\":\"2026-09-30 15:30\""),"000216");assertEquals(Instant.parse("2026-09-30T07:30:00Z").toEpochMilli(),v.getJSONObject("estimate").getLong("dataTime"));}
 @Test public void absentEstimatePercentIsNotComputedFromLatestNav() throws Exception {JSONObject v=FundValuation.parse(raw("\"GSZ\":3.1368,\"GZTIME\":\"2026-09-30 15:30\""),"000216");assertFalse(FundValuation.quote(v,0).has("change"));}
 @Test(expected=Exception.class) public void cannotSubstituteOtherFund() throws Exception {FundValuation.parse(raw("\"GSZ\":3.1368"),"000001");}
 @Test(expected=Exception.class) public void oldHtmlRedirectIsRejected() throws Exception {FundValuation.parse("<!doctype html>notfound","000216");}
}
