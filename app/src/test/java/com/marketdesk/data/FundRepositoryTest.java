package com.marketdesk.data;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;
import org.junit.Test;

public class FundRepositoryTest {
  private final AtomicLong now = new AtomicLong(1791424800000L);

  private String valuation(String code) {
    return "{\"success\":true,\"data\":[{\"FCODE\":\""
        + code
        + "\",\"NAV\":1.5,\"PDATE\":\"2026-09-30\",\"GSZ\":null}]}";
  }

  @Test
  public void cachedNavIsAnIndependentCopyAndExpires() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    FundRepository repository =
        new FundRepository(
            (url, charset) -> {
              calls.incrementAndGet();
              return valuation("000001");
            },
            now::get,
            (code, nav) -> new JSONObject());
    repository.valuation("000001").getJSONObject("nav").put("value", 99);
    assertEquals(1.5, repository.valuation("000001").getJSONObject("nav").getDouble("value"), 0);
    assertEquals(1, calls.get());
    now.addAndGet(61000);
    repository.valuation("000001");
    assertEquals(2, calls.get());
  }

  @Test
  public void unavailableFirstFundHostFallsBackToSecond() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    FundRepository repository =
        new FundRepository(
            (url, charset) -> {
              calls.incrementAndGet();
              if (url.contains("tiantianfunds")) throw new IOException();
              return valuation("000001");
            },
            now::get,
            (code, nav) -> new JSONObject());
    assertEquals(1.5, repository.valuation("000001").getJSONObject("nav").getDouble("value"), 0);
    assertEquals(2, calls.get());
  }

  @Test
  public void referenceEnrichmentDoesNotReplaceOrContaminateNavCache() throws Exception {
    FundRepository repository =
        new FundRepository(
            (url, charset) -> valuation("000001"),
            now::get,
            (code, nav) -> {
              assertEquals("000001", code);
              assertEquals(1.5, nav.optDouble("value"), 0);
              return new JSONObject();
            });
    assertTrue(repository.monitored("000001").has("holdingsReference"));
    assertFalse(repository.valuation("000001").has("holdingsReference"));
  }

  @Test
  public void etfKeepsNavWhenIndependentIopvRequestFails() throws Exception {
    FundRepository repository =
        new FundRepository(
            (url, charset) -> {
              if (url.contains("ulist.np")) throw new IOException();
              return valuation("518880");
            },
            now::get,
            (code, nav) -> new JSONObject());
    JSONObject result = repository.etf("1.518880");
    assertTrue(result.has("nav"));
    assertFalse(result.has("iopv"));
    assertTrue(result.has("iopvError"));
    result.getJSONObject("nav").put("value", 99);
    assertEquals(1.5, repository.etf("1.518880").getJSONObject("nav").getDouble("value"), 0);
  }
}
