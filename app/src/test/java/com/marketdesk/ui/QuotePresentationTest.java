package com.marketdesk.ui;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

public class QuotePresentationTest {
  private final long now = 1791424800000L;

  @Test
  public void fundDetailsKeepOfficialNavAndReferenceDistinct() throws Exception {
    JSONObject values =
        new JSONObject()
            .put("type", "fund")
            .put("nav", new JSONObject().put("value", 1.5).put("date", "2026-09-30"))
            .put(
                "holdingsReference",
                new JSONObject().put("referenceNav", 1.6).put("contribution", 6.67));
    JSONObject quote =
        new JSONObject()
            .put("price", 1.5)
            .put("source", "fixture")
            .put("received", now)
            .put("fundMetrics", values);
    String text = QuotePresentation.details("F:000001", quote, false, "", "A股", now);
    assertTrue(text.startsWith("基金代码：F:000001"));
    assertTrue(text.contains("已公布净值：1.5000"));
    assertTrue(text.contains("参考净值：1.6000"));
    assertFalse(text.contains("交叉验证："));
    assertFalse(text.contains("最新价："));
  }

  @Test
  public void failedStockRefreshShowsOriginalCacheAndFailure() throws Exception {
    JSONObject quote =
        new JSONObject()
            .put("price", 100)
            .put("change", 0)
            .put("unit", "USD")
            .put("source", "fixture")
            .put("received", now)
            .put("time", "10-08 10:00");
    String text = QuotePresentation.details("Y:AAPL", quote, true, "网络超时", "", now);
    assertTrue(text.contains("最新价：100.000 USD"));
    assertTrue(text.contains("涨跌幅：+0.00%"));
    assertTrue(text.contains("网络超时，保留缓存"));
    assertTrue(text.contains("交叉验证："));
  }

  @Test
  public void unavailableQuoteIsNotRenderedAsZeroPrice() {
    String text = QuotePresentation.details("Y:AAPL", new JSONObject(), false, "等待行情", "", now);
    assertTrue(text.contains("最新价：暂无行情"));
    assertTrue(text.contains("涨跌幅：—"));
    assertFalse(text.contains("最新价：0.000"));
  }
}
