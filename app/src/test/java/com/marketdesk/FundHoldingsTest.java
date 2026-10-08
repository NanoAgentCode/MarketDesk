package com.marketdesk;
import org.json.*;
import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class FundHoldingsTest {
 private final long now=Instant.parse("2026-10-08T02:00:00Z").toEpochMilli();
 private String raw(String stocks){return "{\"Success\":true,\"Expansion\":\"2026-06-30\",\"Datas\":{\"fundStocks\":["+stocks+"]}}";}
 @Test public void keepsMarketWeightAndReportDate() throws Exception {
  JSONObject h=FundHoldings.parse(raw("{\"GPDM\":\"NVDA\",\"GPJC\":\"英伟达\",\"JZBL\":\"8.14\",\"NEWTEXCH\":\"105\"},{\"GPDM\":\"00522\",\"JZBL\":\"5.62\",\"NEWTEXCH\":\"116\"},{\"GPDM\":\"603986\",\"JZBL\":\"5.51\",\"NEWTEXCH\":\"1\"}"),"100055",now);
  assertEquals("2026-06-30",h.getString("reportDate"));assertEquals(19.27,h.getDouble("disclosedWeight"),0.0001);
  assertEquals("USD",h.getJSONArray("stocks").getJSONObject(0).getString("currency"));assertEquals("116.00522",h.getJSONArray("stocks").getJSONObject(1).getString("secid"));
 }
 @Test public void unknownMarketsRemainVisible() throws Exception {JSONObject s=FundHoldings.parse(raw("{\"GPDM\":\"ABC\",\"JZBL\":\"40\",\"NEWTEXCH\":\"999\"}"),"000001",now).getJSONArray("stocks").getJSONObject(0);assertFalse(s.has("currency"));assertEquals(40,s.getDouble("weight"),0);}
 @Test(expected=Exception.class) public void duplicateHoldingsAreRejected() throws Exception {FundHoldings.parse(raw("{\"GPDM\":\"NVDA\",\"JZBL\":20,\"NEWTEXCH\":105},{\"GPDM\":\"NVDA\",\"JZBL\":20,\"NEWTEXCH\":105}"),"017436",now);}
 @Test(expected=Exception.class) public void totalWeightCannotExceedNav() throws Exception {FundHoldings.parse(raw("{\"GPDM\":\"NVDA\",\"JZBL\":101,\"NEWTEXCH\":105}"),"017436",now);}
 @Test(expected=Exception.class) public void futureReportsAreRejected() throws Exception {FundHoldings.parse(raw("").replace("2026-06-30","2026-12-31"),"000001",now);}
 @Test public void linkedFundWithoutDirectStocksIsNotReplacedByItsEtf() throws Exception {assertEquals(0,FundHoldings.parse(raw(""),"013308",now).getJSONArray("stocks").length());}
 private String daily(String code,int market,String lines){return "{\"rc\":0,\"data\":{\"code\":\""+code+"\",\"market\":"+market+",\"klines\":["+lines+"]}}";}
 @Test public void historicalReturnUsesNavDateRatherThanLastSession() throws Exception {JSONObject r=FundHoldings.eastmoneyHistory(daily("NVDA",105,"\"2026-09-28,10,10\",\"2026-09-29,10,11\",\"2026-10-07,12,13.2\""),"105.NVDA","2026-09-29",now);assertEquals(1.2,r.getDouble("ratio"),0.00001);assertEquals("2026-09-29",r.getString("baseDate"));assertEquals("2026-10-07",r.getString("date"));}
 @Test public void nonTradingNavDateUsesMostRecentEarlierClose() throws Exception {JSONObject r=FundHoldings.eastmoneyHistory(daily("600519",1,"\"2026-09-25,10,10\",\"2026-09-28,11,11\""),"1.600519","2026-09-27",now);assertEquals("2026-09-25",r.getString("baseDate"));assertEquals(1.1,r.getDouble("ratio"),0.00001);}
 @Test(expected=Exception.class) public void missingBaselineCannotUseFirstLaterPrice() throws Exception {FundHoldings.eastmoneyHistory(daily("NVDA",105,"\"2026-10-07,10,11\""),"105.NVDA","2026-09-29",now);}
 @Test(expected=Exception.class) public void anotherSymbolCannotSupplyHistory() throws Exception {FundHoldings.eastmoneyHistory(daily("AAPL",105,"\"2026-09-29,10,10\",\"2026-10-07,11,11\""),"105.NVDA","2026-09-29",now);}
 @Test(expected=Exception.class) public void staleHistoryIsRejected() throws Exception {FundHoldings.eastmoneyHistory(daily("NVDA",105,"\"2026-09-29,10,10\""),"105.NVDA","2026-09-29",now+20L*86400000);}
 @Test public void zeroReturnIsValid() throws Exception {assertEquals(1,FundHoldings.eastmoneyHistory(daily("NVDA",105,"\"2026-09-29,10,10\",\"2026-10-07,10,10\""),"105.NVDA","2026-09-29",now).getDouble("ratio"),0);}
 @Test public void yahooFxRetainsCurrencyAndBaseline() throws Exception {
  long base=Instant.parse("2026-09-29T00:00:00Z").getEpochSecond(),last=Instant.parse("2026-10-08T01:00:00Z").getEpochSecond();
  String raw="{\"chart\":{\"result\":[{\"meta\":{\"symbol\":\"CNY=X\",\"currency\":\"CNY\",\"exchangeTimezoneName\":\"Europe/London\",\"regularMarketPrice\":7.1,\"regularMarketTime\":"+last+"},\"timestamp\":["+base+","+last+"],\"indicators\":{\"quote\":[{\"close\":[7,7.1]}]}}]}}";
  JSONObject r=FundHoldings.yahooHistory(raw,"CNY=X","2026-09-29","CNY",now);assertEquals(7.1/7,r.getDouble("ratio"),0.00001);assertEquals("2026-09-29",r.getString("baseDate"));
 }
 @Test public void hongKongYahooSymbolsKeepFourDigits() throws Exception {JSONObject h=FundHoldings.parse(raw("{\"GPDM\":\"700\",\"JZBL\":40,\"NEWTEXCH\":116}"),"005827",now);assertEquals("0700.HK",h.getJSONArray("stocks").getJSONObject(0).getString("yahoo"));}
 @Test public void latestBatchUsesMarketAndActualTimestamp() throws Exception {
  String raw="{\"rc\":0,\"data\":{\"diff\":[{\"f12\":\"NVDA\",\"f13\":105,\"f2\":12.1,\"f124\":"+(now/1000-86400)+"}]}}";
  JSONObject history=FundHoldings.eastmoneyHistory(daily("NVDA",105,"\"2026-09-29,10,10\",\"2026-10-06,11,11\""),"105.NVDA","2026-09-29",now);
  JSONObject r=FundHoldings.withLatest(history,FundHoldings.latestBatch(raw,now).get("105.NVDA"),"105.NVDA",now);
  assertEquals(1.21,r.getDouble("ratio"),0.00001);assertEquals("quote",r.getString("kind"));assertEquals(now-86400000,r.getLong("time"));
 }
 @Test public void oldLiveQuoteCannotReplaceNewerDailyHistory() throws Exception {JSONObject h=FundHoldings.eastmoneyHistory(daily("NVDA",105,"\"2026-09-29,10,10\",\"2026-10-07,11,11\""),"105.NVDA","2026-09-29",now);JSONObject r=FundHoldings.withLatest(h,new JSONObject().put("price",9).put("time",now-3*86400000L),"105.NVDA",now);assertEquals(1.1,r.getDouble("ratio"),0.00001);}
 @Test public void batchMissingPricesDoNotBecomeZero() throws Exception {assertTrue(FundHoldings.latestBatch("{\"data\":{\"diff\":[{\"f12\":\"NVDA\",\"f13\":105,\"f2\":\"-\",\"f124\":"+(now/1000)+"}]}}",now).isEmpty());}
 @Test public void knownInternationalHoldingsRequireBothCodeAndName() throws Exception {
  JSONObject h=FundHoldings.parse(raw("{\"GPDM\":\"000660\",\"GPJC\":\"SK海力士\",\"JZBL\":4.62,\"NEWTEXCH\":\"--\"},{\"GPDM\":\"285A\",\"GPJC\":\"KIOXIA\",\"JZBL\":4.58,\"NEWTEXCH\":\"--\"},{\"GPDM\":\"005930\",\"GPJC\":\"三星电子\",\"JZBL\":4.58,\"NEWTEXCH\":\"--\"}"),"100055",now);
  assertEquals("Y:000660.KS",h.getJSONArray("stocks").getJSONObject(0).getString("secid"));assertEquals("JPY",h.getJSONArray("stocks").getJSONObject(1).getString("currency"));assertEquals("KRW",h.getJSONArray("stocks").getJSONObject(2).getString("currency"));
  assertFalse(FundHoldings.parse(raw("{\"GPDM\":\"000660\",\"GPJC\":\"其他公司\",\"JZBL\":4.62,\"NEWTEXCH\":\"--\"}"),"100055",now).getJSONArray("stocks").getJSONObject(0).has("currency"));
 }
 @Test public void adjustedHistoryDoesNotInterpretSplitAsALoss() throws Exception {
  long base=Instant.parse("2026-09-29T14:00:00Z").getEpochSecond(),last=Instant.parse("2026-10-07T20:00:00Z").getEpochSecond();
  String raw="{\"chart\":{\"result\":[{\"meta\":{\"symbol\":\"NVDA\",\"currency\":\"USD\",\"exchangeTimezoneName\":\"America/New_York\",\"regularMarketPrice\":55,\"regularMarketTime\":"+last+"},\"timestamp\":["+base+","+last+"],\"indicators\":{\"quote\":[{\"close\":[100,55]}],\"adjclose\":[{\"adjclose\":[50,55]}]}}]}}";
  JSONObject history=FundHoldings.yahooHistory(raw,"NVDA","2026-09-29","USD",now);assertEquals(1.1,history.getDouble("ratio"),0.000001);
  assertEquals(1.2,FundHoldings.withLatest(history,new JSONObject().put("price",60).put("time",now-1000),"105.NVDA",now).getDouble("ratio"),0.000001);
 }
 @Test public void impossibleFutureBatchTimeIsRejected() throws Exception {assertTrue(FundHoldings.latestBatch("{\"data\":{\"diff\":[{\"f12\":\"NVDA\",\"f13\":105,\"f2\":10,\"f124\":"+(now/1000+3600)+"}]}}",now).isEmpty());}
}
