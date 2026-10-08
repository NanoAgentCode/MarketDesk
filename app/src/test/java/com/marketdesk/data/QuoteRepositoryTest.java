package com.marketdesk.data;

import static org.junit.Assert.*;

import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.Test;

public class QuoteRepositoryTest {
  private final AtomicLong now = new AtomicLong(1791424800000L);

  private JSONObject quote() throws Exception {
    return new JSONObject()
        .put("price", 110)
        .put("change", 10)
        .put("unit", "USD")
        .put("dataTime", now.get())
        .put("received", now.get())
        .put("source", "fixture");
  }

  @Test
  public void onlySecondarySourcesUseTheSixtySecondCache() throws Exception {
    AtomicInteger primary = new AtomicInteger(), secondary = new AtomicInteger();
    QuoteRepository repository =
        new QuoteRepository(
            code -> {
              if (code.startsWith("Y:")) primary.incrementAndGet();
              else secondary.incrementAndGet();
              return quote();
            },
            secid -> new JSONObject(),
            now::get);
    assertEquals("agree", repository.fetch("Y:AAPL").getString("verification"));
    repository.fetch("Y:AAPL");
    assertEquals(2, primary.get());
    assertEquals(1, secondary.get());
    now.addAndGet(61000);
    repository.fetch("Y:AAPL");
    assertEquals(3, primary.get());
    assertEquals(2, secondary.get());
  }

  @Test
  public void callerMutationDoesNotPoisonCachedSecondaryQuote() throws Exception {
    QuoteRepository repository =
        new QuoteRepository(code -> quote(), secid -> new JSONObject(), now::get);
    JSONObject first = repository.fetch("Y:AAPL");
    first.getJSONArray("verificationSamples").getJSONObject(1).put("price", 999);
    assertEquals(
        110,
        repository
            .fetch("Y:AAPL")
            .getJSONArray("verificationSamples")
            .getJSONObject(1)
            .getDouble("price"),
        0);
  }

  @Test
  public void sourceFailureDoesNotDiscardAnAvailableProvider() throws Exception {
    QuoteRepository repository =
        new QuoteRepository(
            code -> {
              if (code.startsWith("Y:")) throw new SocketTimeoutException();
              return quote();
            },
            secid -> new JSONObject(),
            now::get);
    JSONObject result = repository.fetch("Y:AAPL");
    assertEquals("single", result.getString("verification"));
    assertEquals("tencent", result.getString("origin"));
    assertEquals(1, result.getJSONArray("verificationFailures").length());
  }

  @Test
  public void fundQuotesKeepTheirOwnTypeWithoutStockVerification() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    QuoteRepository repository =
        new QuoteRepository(
            code -> {
              calls.incrementAndGet();
              return new JSONObject().put("kind", "nav").put("price", 1);
            },
            secid -> {
              fail("fund must not query ETF metrics");
              return null;
            },
            now::get);
    assertEquals("nav", repository.fetch("F:000001").getString("kind"));
    assertEquals(1, calls.get());
  }

  @Test
  public void completeFailureIncludesEachProviderReason() throws Exception {
    QuoteRepository repository =
        new QuoteRepository(
            code -> {
              throw new SocketTimeoutException();
            },
            secid -> new JSONObject(),
            now::get);
    try {
      repository.fetch("Y:AAPL");
      fail("expected failure");
    } catch (Exception error) {
      assertTrue(error.getMessage().contains("yahoo"));
      assertTrue(error.getMessage().contains("tencent"));
      assertTrue(error.getMessage().contains("网络超时"));
    }
  }
}
