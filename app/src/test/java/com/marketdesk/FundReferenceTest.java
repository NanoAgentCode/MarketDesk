package com.marketdesk;
import org.json.*;
import org.junit.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.Assert.*;

public class FundReferenceTest {
 private final long now=Instant.parse("2026-10-08T02:00:00Z").toEpochMilli();
 private JSONObject nav() throws Exception {return new JSONObject("{\"value\":1,\"date\":\"2026-09-29\"}");}
 private JSONObject holdings(String stocks) throws Exception {return new JSONObject("{\"reportDate\":\"2026-06-30\",\"stocks\":["+stocks+"]}");}
 private String stock(String id,int weight,String currency){return "{\"secid\":\""+id+"\",\"code\":\""+id+"\",\"name\":\"样本\",\"weight\":"+weight+",\"currency\":\""+currency+"\"}";}
 private JSONObject sample(double ratio) throws Exception {return new JSONObject().put("ratio",ratio).put("navDate","2026-09-29").put("baseDate","2026-09-29").put("date","2026-10-07").put("time",now-86400000).put("source","样本");}
 @Test public void partialDisclosureIsNotNormalizedToWholeFund() throws Exception {JSONObject r=FundReference.calculate(nav(),holdings(stock("1.X",40,"CNY")),Map.of("1.X",sample(1.1)),now);assertEquals(4,r.getDouble("contribution"),0.000001);assertEquals(1.04,r.getDouble("referenceNav"),0.000001);assertEquals(40,r.getDouble("matchedWeight"),0);assertEquals(60,r.getDouble("unknownWeight"),0);}
 @Test public void missingPriceKeepsContributionButSuppressesReferenceNav() throws Exception {JSONObject r=FundReference.calculate(nav(),holdings(stock("1.X",40,"CNY")+","+stock("1.Y",20,"CNY")),Map.of("1.X",sample(1.1)),now);assertEquals(4,r.getDouble("contribution"),0.00001);assertFalse(r.has("referenceNav"));assertEquals(20,r.getDouble("missingWeight"),0);}
 @Test public void fxAndAssetReturnsCompoundRatherThanAdd() throws Exception {JSONObject r=FundReference.calculate(nav(),holdings(stock("105.X",50,"USD")),Map.of("105.X",sample(1.1),"USD",sample(1.02)),now);assertEquals(6.1,r.getDouble("contribution"),0.00001);assertEquals(1.061,r.getDouble("referenceNav"),0.00001);}
 @Test public void missingFxIsNotAssumedUnchanged() throws Exception {JSONObject r=FundReference.calculate(nav(),holdings(stock("105.X",50,"USD")),Map.of("105.X",sample(1.1)),now);assertFalse(r.has("referenceNav"));assertFalse(r.has("contribution"));assertEquals(50,r.getDouble("missingWeight"),0);}
 @Test public void zeroChangeRemainsAvailable() throws Exception {JSONObject r=FundReference.calculate(nav(),holdings(stock("1.X",40,"CNY")),Map.of("1.X",sample(1)),now);assertEquals(0,r.getDouble("contribution"),0);assertEquals(1,r.getDouble("referenceNav"),0);}
 @Test public void mixedMarketsUseTheirOwnCurrencies() throws Exception {JSONObject r=FundReference.calculate(nav(),holdings(stock("1.X",30,"CNY")+","+stock("116.Y",30,"HKD")+","+stock("105.Z",30,"USD")),Map.of("1.X",sample(1.1),"116.Y",sample(1),"105.Z",sample(1),"HKD",sample(1.02),"USD",sample(0.99)),now);assertEquals(3.3,r.getDouble("contribution"),0.00001);}
 @Test public void unknownMarketDoesNotBecomeAnAShare() throws Exception {JSONObject h=holdings(stock("999.X",40,"CNY"));h.getJSONArray("stocks").getJSONObject(0).remove("currency");JSONObject r=FundReference.calculate(nav(),h,Map.of("999.X",sample(1.1)),now);assertFalse(r.has("referenceNav"));}
 @Test public void wrongBaselineCannotMixWithOfficialNav() throws Exception {JSONObject s=sample(1.1).put("navDate","2026-09-30");JSONObject r=FundReference.calculate(nav(),holdings(stock("1.X",40,"CNY")),Map.of("1.X",s),now);assertFalse(r.has("referenceNav"));}
 @Test public void staleReportsCannotProduceReferenceNav() throws Exception {JSONObject h=holdings(stock("1.X",40,"CNY")).put("reportDate","2025-12-31");JSONObject r=FundReference.calculate(nav(),h,Map.of("1.X",sample(1.1)),now);assertFalse(r.has("referenceNav"));assertTrue(r.getString("reason").contains("持仓"));}
 @Test public void outdatedNavDoesNotBecomeTodaysEstimate() throws Exception {JSONObject n=nav().put("date","2026-06-30");JSONObject r=FundReference.calculate(n,holdings(stock("1.X",40,"CNY")),Map.of("1.X",sample(1.1)),now);assertFalse(r.has("referenceNav"));}
 @Test public void referenceDoesNotReplaceMainNavOrThirdPartyEstimate() throws Exception {JSONObject values=new JSONObject().put("code","000001").put("nav",nav().put("dataTime",now-86400000).put("source","正式净值")).put("holdingsReference",new JSONObject().put("referenceNav",1.04).put("contribution",4));JSONObject q=FundValuation.quote(values,now);assertEquals("nav",q.getString("kind"));assertEquals(1,q.getDouble("price"),0);assertFalse(q.has("change"));assertEquals("参考",q.getString("verificationShort"));values.put("estimate",new JSONObject().put("value",1.02).put("time","2026-10-08 10:00").put("dataTime",now).put("source","第三方"));assertEquals(1.02,FundValuation.quote(values,now).getDouble("price"),0);}
}
