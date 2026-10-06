package com.marketdesk;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class EastmoneyParserTest {
 private static final String SECID="2.931250";
 @Test public void decimalLatestIsNotDividedAgain() throws Exception {
  JSONObject q=EastmoneyParser.latest("{\"rc\":0,\"data\":{\"f43\":1243.61,\"f60\":1194.45,\"f170\":4.12,\"f59\":2,\"f86\":1790756997}}",SECID);
  assertEquals(1243.61,q.getDouble("price"),0.0001);assertEquals(4.12,q.getDouble("change"),0.0001);
 }
 @Test public void missingPercentUsesPreviousClose() throws Exception {
  JSONObject q=EastmoneyParser.latest("{\"rc\":0,\"data\":{\"f43\":105,\"f60\":100,\"f170\":\"-\",\"f86\":1790756997}}",SECID);
  assertEquals(5,q.getDouble("change"),0.0001);
 }
 @Test public void minuteQuoteUsesCloseAndDayBaseline() throws Exception {
  JSONObject q=EastmoneyParser.trends("{\"rc\":0,\"data\":{\"code\":\"931250\",\"market\":2,\"preClose\":1194.45,\"trends\":[\"2026-09-30 15:00,1243.99,1243.61,1246.02,1242,1769756,4225411056,1250.86\"]}}",SECID);
  assertEquals(1243.61,q.getDouble("price"),0.0001);assertEquals(4.1157,q.getDouble("change"),0.001);assertEquals("minute",q.getString("kind"));
  assertEquals(java.time.Instant.parse("2026-09-30T07:00:00Z").toEpochMilli(),q.getLong("dataTime"));
 }
 @Test public void dailyKeepsDateAndLabel() throws Exception {
  JSONObject q=EastmoneyParser.daily("{\"rc\":0,\"data\":{\"code\":\"931250\",\"market\":2,\"klines\":[\"2026-09-30,1186.11,1243.61,1247.7,1185.59,7282727,19513213394.2,5.20,4.12,49.16,0.83\"]}}",SECID);
  assertEquals(4.12,q.getDouble("change"),0.0001);assertEquals("daily",q.getString("kind"));assertTrue(q.getString("source").contains("非实时"));
 }
 @Test(expected=Exception.class) public void missingDataIsNotZeroQuote() throws Exception {EastmoneyParser.trends("{\"rc\":0,\"data\":null}",SECID);}
 @Test(expected=Exception.class) public void rejectsWrongIndex() throws Exception {EastmoneyParser.trends("{\"rc\":0,\"data\":{\"code\":\"000001\",\"market\":1,\"preClose\":100,\"trends\":[\"2026-09-30 15:00,101,102\"]}}",SECID);}
 @Test(expected=Exception.class) public void rejectsEmptySeries() throws Exception {EastmoneyParser.trends("{\"rc\":0,\"data\":{\"trends\":[]}}",SECID);}
}
