package com.marketdesk.data;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import org.json.JSONObject;

/**
 * Reuses in-flight work and caches JSON successes/failures without exposing mutable cached values.
 */
public final class JsonRequestCache {
  private static final class Entry {
    final FutureTask<JSONObject> task;
    long completedAt;

    Entry(FutureTask<JSONObject> task) {
      this.task = task;
    }
  }

  private final LongSupplier clock;
  private final ExecutorService pool;
  private final Map<String, Entry> entries = new LinkedHashMap<>();

  public JsonRequestCache(LongSupplier clock, ExecutorService pool) {
    this.clock = clock;
    this.pool = pool;
  }

  public synchronized FutureTask<JSONObject> request(
      String key, long ttl, Callable<JSONObject> load) {
    long now = clock.getAsLong();
    Entry existing = entries.get(key);
    if (existing != null) {
      if (!existing.task.isDone()) return existing.task;
      try {
        if (existing.task.get().has("error")) ttl = Math.min(ttl, ReferencePolicy.PRICE_CACHE_MS);
      } catch (Exception ignored) {
        ttl = Math.min(ttl, ReferencePolicy.PRICE_CACHE_MS);
      }
      if (now - existing.completedAt < ttl) return existing.task;
    }
    FutureTask<JSONObject> task =
        new FutureTask<>(
            () -> {
              try {
                return load.call();
              } catch (Exception exception) {
                return new JSONObject()
                    .put("error", exception.getMessage() == null ? "取数失败" : exception.getMessage());
              } finally {
                synchronized (JsonRequestCache.this) {
                  Entry entry = entries.get(key);
                  if (entry != null) entry.completedAt = clock.getAsLong();
                }
              }
            });
    entries.put(key, new Entry(task));
    trimCompletedEntries();
    pool.execute(task);
    return task;
  }

  private void trimCompletedEntries() {
    if (entries.size() <= ReferencePolicy.CACHE_ENTRIES) return;
    Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
    while (iterator.hasNext() && entries.size() > ReferencePolicy.CACHE_ENTRIES) {
      if (iterator.next().getValue().task.isDone()) iterator.remove();
    }
  }

  public static JSONObject await(FutureTask<JSONObject> task, long deadline) throws Exception {
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0) throw new TimeoutException("持仓参考查询超时");
    return new JSONObject(task.get(remaining, TimeUnit.NANOSECONDS).toString());
  }
}
