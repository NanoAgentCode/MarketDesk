package com.marketdesk.network;

import static org.junit.Assert.*;

import java.time.Instant;
import org.junit.Test;

public class MarketUrlsTest {
  @Test
  public void referenceHistoryUsesAdjustmentAndBaselineWindow() {
    String url =
        MarketUrls.stockHistory(
            "105.NVDA", "2026-09-29", Instant.parse("2026-10-08T02:00:00Z").toEpochMilli());
    assertTrue(url.contains("fqt=1"));
    assertTrue(url.contains("beg=20260922"));
    assertTrue(url.contains("end=20261008"));
    assertTrue(MarketUrls.eastmoneyDaily("105.NVDA").contains("fqt=0"));
  }

  @Test
  public void yahooHistoryUsesInjectedClockAndEscapedSymbol() throws Exception {
    long now = Instant.parse("2026-10-08T02:00:00Z").toEpochMilli();
    String url = MarketUrls.yahooHistory("CNY=X", "2026-09-29", now);
    assertTrue(url.contains("CNY%3DX"));
    assertTrue(url.contains("period2=" + (now / 1000 + 86400)));
  }
}
