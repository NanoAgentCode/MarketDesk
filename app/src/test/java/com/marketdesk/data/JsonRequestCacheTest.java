package com.marketdesk.data;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;
import org.junit.*;

public class JsonRequestCacheTest {
  private final ExecutorService pool = Executors.newFixedThreadPool(2);
  private final AtomicLong now = new AtomicLong(1000);
  private final JsonRequestCache cache = new JsonRequestCache(now::get, pool);

  private long deadline() {
    return System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
  }

  @After
  public void stop() {
    pool.shutdownNow();
  }

  @Test
  public void simultaneousConsumersReuseInFlightLoad() throws Exception {
    CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    FutureTask<JSONObject> first =
        cache.request(
            "shared",
            60000,
            () -> {
              calls.incrementAndGet();
              started.countDown();
              release.await();
              return new JSONObject().put("value", 1);
            });
    assertTrue(started.await(1, TimeUnit.SECONDS));
    FutureTask<JSONObject> second =
        cache.request(
            "shared",
            60000,
            () -> {
              fail("duplicate in-flight request");
              return null;
            });
    assertSame(first, second);
    release.countDown();
    assertEquals(1, JsonRequestCache.await(second, deadline()).getInt("value"));
    assertEquals(1, calls.get());
  }

  @Test
  public void consumersCannotMutateTheCachedObject() throws Exception {
    FutureTask<JSONObject> job =
        cache.request("value", 60000, () -> new JSONObject().put("value", 1));
    JsonRequestCache.await(job, deadline()).put("value", 99);
    assertEquals(1, JsonRequestCache.await(job, deadline()).getInt("value"));
  }

  @Test
  public void failedDailyDataRetriesAfterOneMinute() throws Exception {
    FutureTask<JSONObject> failed =
        cache.request(
            "holdings",
            ReferencePolicy.DAY_MS,
            () -> {
              throw new IOException("unavailable");
            });
    assertTrue(JsonRequestCache.await(failed, deadline()).has("error"));
    now.addAndGet(61000);
    assertEquals(
        2,
        JsonRequestCache.await(
                cache.request(
                    "holdings", ReferencePolicy.DAY_MS, () -> new JSONObject().put("value", 2)),
                deadline())
            .getInt("value"));
  }

  @Test
  public void deadlineDoesNotCancelARequestSharedByAnotherConsumer() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    FutureTask<JSONObject> job =
        cache.request(
            "shared",
            60000,
            () -> {
              release.await();
              return new JSONObject().put("value", 1);
            });
    try {
      JsonRequestCache.await(job, System.nanoTime() - 1);
      fail("expected deadline");
    } catch (TimeoutException expected) {
    }
    assertFalse(job.isCancelled());
    release.countDown();
    assertEquals(1, JsonRequestCache.await(job, deadline()).getInt("value"));
  }
}
