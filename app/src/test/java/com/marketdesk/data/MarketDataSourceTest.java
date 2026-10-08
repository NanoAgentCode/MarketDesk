package com.marketdesk.data;

import static org.junit.Assert.*;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

public class MarketDataSourceTest {
  private final long now = Instant.parse("2026-10-08T02:00:00Z").toEpochMilli();

  private String yahoo(String symbol) {
    return "{\"chart\":{\"result\":[{\"meta\":{\"symbol\":\""
        + symbol
        + "\",\"regularMarketPrice\":110,\"chartPreviousClose\":80,\"previousClose\":100,"
        + "\"regularMarketTime\":"
        + now / 1000
        + ",\"currency\":\"USD\"}}]}}";
  }

  @Test
  public void yahooUsesPreviousSessionCloseInsteadOfChartBaseline() throws Exception {
    MarketDataSource source =
        new MarketDataSource((url, charset) -> yahoo("AAPL"), () -> now, code -> null);
    JSONObject quote = source.fetch("Y:AAPL");
    assertEquals(10, quote.getDouble("change"), 0.000001);
    assertEquals(now, quote.getLong("received"));
    assertEquals("10-08 10:00", quote.getString("time"));
  }

  @Test(expected = IOException.class)
  public void anotherYahooSymbolCannotSupplyQuote() throws Exception {
    new MarketDataSource((url, charset) -> yahoo("MSFT"), () -> now, code -> null).fetch("Y:AAPL");
  }

  @Test
  public void eastmoneyKeepsLatestThenTrendsThenDailyFallbackOrder() throws Exception {
    List<String> requested = new ArrayList<>();
    MarketDataSource source =
        new MarketDataSource(
            (url, charset) -> {
              requested.add(url);
              if (!url.contains("kline/get")) throw new IOException("unavailable");
              return "{\"data\":{\"code\":\"600519\",\"market\":1,\"klines\":[\"2026-09-30,10,11,12,9,0,0,0,10\"]}}";
            },
            () -> now,
            code -> null);
    JSONObject quote = source.fetch("E:1.600519");
    assertEquals(3, requested.size());
    assertTrue(requested.get(0).contains("stock/get"));
    assertTrue(requested.get(1).contains("trends2/get"));
    assertTrue(requested.get(2).contains("fqt=0"));
    assertEquals("2026-09-30 日线", quote.getString("time"));
    assertEquals("daily", quote.getString("kind"));
  }

  @Test
  public void allEastmoneyFailuresRetainEarlierCauses() throws Exception {
    MarketDataSource source =
        new MarketDataSource(
            (url, charset) -> {
              throw new IOException(url);
            },
            () -> now,
            code -> null);
    try {
      source.eastmoney("1.600519");
      fail("expected source failures");
    } catch (IOException error) {
      assertEquals(2, error.getSuppressed().length);
      assertTrue(error.getMessage().contains("kline/get"));
    }
  }

  @Test
  public void tencentRetainsItsResponseEncodingAndMarketTimezone() throws Exception {
    String[] fields = new String[33];
    java.util.Arrays.fill(fields, "");
    fields[2] = "AAPL";
    fields[3] = "110";
    fields[4] = "100";
    fields[30] = "2026-10-07 16:00:00";
    fields[32] = "10";
    MarketDataSource source =
        new MarketDataSource(
            (url, charset) -> {
              assertEquals("GB18030", charset);
              return "v_usAAPL=\"" + String.join("~", fields) + "\";";
            },
            () -> now,
            code -> null);
    assertEquals(
        Instant.parse("2026-10-07T20:00:00Z").toEpochMilli(),
        source.fetch("T:usAAPL").getLong("dataTime"));
  }
}
