package com.marketdesk;
import org.json.*;
import org.junit.*;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class FundReferenceDataTest {
 private final AtomicLong now=new AtomicLong(Instant.parse("2026-10-08T02:00:00Z").toEpochMilli());
 private final ExecutorService pool=Executors.newFixedThreadPool(4);
 private final AtomicInteger holdingsCalls=new AtomicInteger(),historyCalls=new AtomicInteger(),quoteCalls=new AtomicInteger();
 private volatile boolean failHoldings,failHistory,failLatest;
 @After public void stop(){pool.shutdownNow();}
 private String fetch(String url) throws Exception {
  if(url.contains("FundMNInverstPosition")){holdingsCalls.incrementAndGet();if(failHoldings)throw new IOException("持仓失败");return "{\"Success\":true,\"Expansion\":\"2026-06-30\",\"Datas\":{\"fundStocks\":[{\"GPDM\":\"600519\",\"GPJC\":\"贵州茅台\",\"JZBL\":40,\"NEWTEXCH\":1}]}}";}
  if(url.contains("kline/get")){historyCalls.incrementAndGet();if(failHistory)throw new IOException("历史失败");return "{\"rc\":0,\"data\":{\"code\":\"600519\",\"market\":1,\"klines\":[\"2026-09-29,10,10\",\"2026-10-08,10,11\"]}}";}
  if(url.contains("ulist.np/get")){quoteCalls.incrementAndGet();if(failLatest)throw new IOException("最新失败");return "{\"data\":{\"diff\":[{\"f12\":\"600519\",\"f13\":1,\"f2\":11,\"f124\":"+now.get()/1000+"}]}}";}
  throw new IOException("备用接口失败");
 }
 private JSONObject nav() throws Exception {return new JSONObject().put("value",1).put("date","2026-09-29");}
 private FundReferenceData service(){return new FundReferenceData(this::fetch,now::get,pool);}
 @Test public void refreshReusesHoldingsAndSixtySecondPrices() throws Exception {FundReferenceData service=service();assertEquals(1.04,service.reference("000001",nav()).getDouble("referenceNav"),0.00001);service.reference("000001",nav());assertEquals(1,holdingsCalls.get());assertEquals(1,historyCalls.get());assertEquals(1,quoteCalls.get());now.addAndGet(61000);service.reference("000001",nav());assertEquals(1,holdingsCalls.get());assertEquals(2,historyCalls.get());assertEquals(2,quoteCalls.get());}
 @Test public void overlappingFundsShareStockRequests() throws Exception {FundReferenceData service=service();service.reference("000001",nav());service.reference("005827",nav());assertEquals(2,holdingsCalls.get());assertEquals(1,historyCalls.get());assertEquals(1,quoteCalls.get());}
 @Test public void officialNavValueChangeIsNotLostInPriceCache() throws Exception {FundReferenceData service=service();service.reference("000001",nav());assertEquals(2.08,service.reference("000001",nav().put("value",2)).getDouble("referenceNav"),0.00001);}
 @Test public void failuresAreCachedWithoutInventingReference() throws Exception {failHistory=true;FundReferenceData service=service();assertFalse(service.reference("000001",nav()).has("referenceNav"));service.reference("000001",nav());assertEquals(1,historyCalls.get());now.addAndGet(61000);failHistory=false;assertTrue(service.reference("000001",nav()).has("referenceNav"));}
 @Test public void holdingsFailureDoesNotThrowOrCreateZeroReference() throws Exception {failHoldings=true;JSONObject r=service().reference("000001",nav());assertFalse(r.has("referenceNav"));assertTrue(r.getString("reason").contains("持仓"));}
 @Test public void holdingsFailureRetriesAfterAMinuteRatherThanADay() throws Exception {failHoldings=true;FundReferenceData service=service();service.reference("000001",nav());service.reference("000001",nav());assertEquals(1,holdingsCalls.get());now.addAndGet(61000);failHoldings=false;assertTrue(service.reference("000001",nav()).has("referenceNav"));assertEquals(2,holdingsCalls.get());}
 @Test public void optionalLiveFailureKeepsDatedDailyReference() throws Exception {failLatest=true;JSONObject r=service().reference("000001",nav());assertTrue(r.has("referenceNav"));assertEquals("daily",r.getJSONArray("stocks").getJSONObject(0).getString("kind"));}
 @Test public void conciseSummaryKeepsDetailedStocksInSeparateDialog() throws Exception {JSONObject r=service().reference("000001",nav());String summary=FundReference.summary(r);assertFalse(summary.contains("贵州茅台"));assertTrue(summary.contains("未披露仓位假设不变"));assertTrue(summary.split("\n").length<=12);assertTrue(FundReference.stockDetails(r).contains("贵州茅台"));}
}
