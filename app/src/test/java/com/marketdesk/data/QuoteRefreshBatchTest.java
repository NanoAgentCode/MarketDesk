package com.marketdesk.data;

import static org.junit.Assert.*;

import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.Test;

public class QuoteRefreshBatchTest {
  @Test
  public void partialFailurePublishesSuccessAndErrorSeparately() {
    AtomicInteger logged = new AtomicInteger();
    QuoteRefreshBatch batch =
        new QuoteRefreshBatch(
            code -> {
              if (code.equals("Y:BAD")) throw new SocketTimeoutException();
              return new JSONObject().put("price", 1);
            },
            (code, error) -> logged.incrementAndGet());
    QuoteRefreshBatch.Result result =
        batch.fetch(
            Arrays.asList(new String[] {"valid", "Y:GOOD"}, new String[] {"invalid", "Y:BAD"}));
    assertEquals(1, result.quotes.size());
    assertTrue(result.quotes.containsKey("Y:GOOD"));
    assertFalse(result.quotes.containsKey("Y:BAD"));
    assertEquals("网络超时，请稍后刷新", result.errors.get("Y:BAD"));
    assertEquals(1, logged.get());
  }

  @Test
  public void completedBatchCannotBeChangedByThePublisher() {
    QuoteRefreshBatch.Result result =
        new QuoteRefreshBatch(code -> new JSONObject(), (code, error) -> {})
            .fetch(Collections.emptyList());
    try {
      result.quotes.put("injected", "{}");
      fail("expected immutable batch");
    } catch (UnsupportedOperationException expected) {
    }
    assertTrue(result.errors.isEmpty());
  }
}
