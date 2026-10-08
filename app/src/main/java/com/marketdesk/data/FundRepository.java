package com.marketdesk.data;

import com.marketdesk.EtfReference;
import com.marketdesk.FundValuation;
import com.marketdesk.network.HttpTransport;
import com.marketdesk.network.MarketUrls;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import org.json.*;

/** Owns fund/ETF metrics and their cache; reference computation is injected independently. */
public final class FundRepository {
  private final HttpTransport http;
  private final LongSupplier clock;
  private final BiFunction<String, JSONObject, JSONObject> references;
  private final Map<String, JSONObject> navCache = new ConcurrentHashMap<>();
  private final Map<String, JSONObject> etfCache = new ConcurrentHashMap<>();

  public FundRepository(
      HttpTransport http,
      LongSupplier clock,
      BiFunction<String, JSONObject, JSONObject> references) {
    this.http = http;
    this.clock = clock;
    this.references = references;
  }

  public JSONObject valuation(String code) throws Exception {
    long now = clock.getAsLong();
    JSONObject cached = navCache.get(code);
    if (cached != null && now - cached.optLong("checkedAt") < ReferencePolicy.PRICE_CACHE_MS) {
      return new JSONObject(cached.toString());
    }
    Exception failure = null;
    for (String host : MarketUrls.FUND_HOSTS) {
      try {
        JSONObject values =
            FundValuation.parse(http.get(MarketUrls.fund(host, code)), code).put("checkedAt", now);
        navCache.put(code, new JSONObject(values.toString()));
        return values;
      } catch (Exception unavailable) {
        failure = unavailable;
      }
    }
    throw failure;
  }

  public JSONObject monitored(String code) throws Exception {
    JSONObject values = valuation(code);
    return values.put("holdingsReference", references.apply(code, values.optJSONObject("nav")));
  }

  public JSONObject etf(String secid) {
    long now = clock.getAsLong();
    JSONObject cached = etfCache.get(secid);
    if (cached != null && now - cached.optLong("checkedAt") < ReferencePolicy.PRICE_CACHE_MS) {
      try {
        return new JSONObject(cached.toString());
      } catch (JSONException ignored) {
      }
    }
    JSONObject values = new JSONObject();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    Future<JSONObject> nav = pool.submit(() -> valuation(secid.substring(2)));
    Future<JSONObject> iopv =
        pool.submit(() -> EtfReference.parse(http.get(MarketUrls.iopv(secid)), secid));
    try {
      values.put("type", "etf").put("code", secid.substring(2)).put("checkedAt", now);
      try {
        JSONObject result = nav.get();
        if (result.has("nav")) values.put("nav", result.getJSONObject("nav"));
        if (result.has("estimate")) values.put("estimate", result.getJSONObject("estimate"));
      } catch (Exception unavailable) {
        values.put("valuationError", "估值/净值暂不可用");
      }
      try {
        values.put("iopv", iopv.get());
      } catch (Exception unavailable) {
        values.put("iopvError", "IOPV暂不可用，成交价仍可查看");
      }
      etfCache.put(secid, new JSONObject(values.toString()));
    } catch (Exception invalidResult) {
      // Preserve partial metrics even when an independent source is unavailable.
    } finally {
      pool.shutdownNow();
    }
    return values;
  }
}
