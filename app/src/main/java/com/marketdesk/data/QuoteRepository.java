package com.marketdesk.data;

import com.marketdesk.EtfReference;
import com.marketdesk.QuoteSources;
import com.marketdesk.QuoteVerification;
import com.marketdesk.ui.QuotePresentation;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.json.*;

/**
 * Source selection, secondary caching and verification; independent of Activity and preferences.
 */
public final class QuoteRepository {
  @FunctionalInterface
  public interface Source {
    JSONObject fetch(String code) throws Exception;
  }

  private final Source source;
  private final Function<String, JSONObject> etfMetrics;
  private final LongSupplier clock;
  private final Map<String, JSONObject> secondaryCache = new ConcurrentHashMap<>();

  public QuoteRepository(
      Source source, Function<String, JSONObject> etfMetrics, LongSupplier clock) {
    this.source = source;
    this.etfMetrics = etfMetrics;
    this.clock = clock;
  }

  public JSONObject fetch(String code) throws Exception {
    if (code.startsWith("F:")) return source.fetch(code);
    Map<String, String> plan = QuoteSources.plan(code);
    ExecutorService pool = Executors.newFixedThreadPool(plan.size());
    List<Future<JSONObject>> jobs = new ArrayList<>();
    List<String> providers = new ArrayList<>();
    String preferred = QuoteSources.provider(code);
    try {
      for (Map.Entry<String, String> entry : plan.entrySet()) {
        providers.add(entry.getKey());
        jobs.add(
            pool.submit(() -> readCandidate(code, entry.getValue(), entry.getKey(), preferred)));
      }
      List<JSONObject> available = new ArrayList<>();
      JSONArray failures = new JSONArray();
      for (int i = 0; i < jobs.size(); i++) {
        try {
          available.add(jobs.get(i).get());
        } catch (ExecutionException failed) {
          Throwable cause = failed.getCause();
          failures.put(
              new JSONObject()
                  .put("source", providers.get(i))
                  .put(
                      "error",
                      QuotePresentation.errorText(
                          cause instanceof Exception
                              ? (Exception) cause
                              : new IOException("获取失败"))));
        }
      }
      if (available.isEmpty()) throw allSourcesFailed(failures);
      JSONObject selected = QuoteVerification.combine(available, failures, clock.getAsLong());
      String secid = EtfReference.secid(code);
      if (!secid.isEmpty()) selected.put("fundMetrics", etfMetrics.apply(secid));
      return selected;
    } finally {
      pool.shutdownNow();
    }
  }

  private JSONObject readCandidate(
      String requested, String candidate, String provider, String preferred) throws Exception {
    long now = clock.getAsLong();
    JSONObject cached = secondaryCache.get(candidate);
    JSONObject quote =
        cached != null
                && !provider.equals(preferred)
                && now - cached.optLong("received") < ReferencePolicy.PRICE_CACHE_MS
            ? new JSONObject(cached.toString())
            : source.fetch(candidate);
    quote.put("origin", provider);
    String unit = QuoteSources.unit(requested);
    if (!unit.isEmpty()) quote.put("unit", unit);
    secondaryCache.put(candidate, new JSONObject(quote.toString()));
    return quote;
  }

  private IOException allSourcesFailed(JSONArray failures) throws Exception {
    StringBuilder reason = new StringBuilder("全部行情源不可用");
    for (int i = 0; i < failures.length(); i++) {
      JSONObject failure = failures.getJSONObject(i);
      reason
          .append("；")
          .append(failure.optString("source"))
          .append("：")
          .append(failure.optString("error"));
    }
    return new IOException(reason.toString());
  }
}
