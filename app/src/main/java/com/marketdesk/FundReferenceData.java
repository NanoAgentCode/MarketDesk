package com.marketdesk;

import com.marketdesk.data.*;
import com.marketdesk.network.HttpTransport;
import com.marketdesk.network.UrlConnectionTransport;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import org.json.*;

/**
 * Coordinates reference requests; source fetching, caching, parsing and calculation are
 * independent.
 */
public final class FundReferenceData {
  @FunctionalInterface
  interface Fetch {
    String get(String url) throws Exception;
  }

  private final LongSupplier clock;
  private final JsonRequestCache cache;
  private final FundReferenceSources sources;

  public FundReferenceData() {
    this(
        new UrlConnectionTransport(),
        System::currentTimeMillis,
        Executors.newFixedThreadPool(ReferencePolicy.REQUEST_THREADS));
  }

  FundReferenceData(Fetch fetch, LongSupplier clock, ExecutorService pool) {
    this((url, charset) -> fetch.get(url), clock, pool);
  }

  public FundReferenceData(HttpTransport http, LongSupplier clock, ExecutorService pool) {
    this.clock = clock;
    this.cache = new JsonRequestCache(clock, pool);
    this.sources = new FundReferenceSources(http, clock);
  }

  public JSONObject reference(String code, JSONObject nav) {
    long now = clock.getAsLong();
    long deadline = System.nanoTime() + ReferencePolicy.DEADLINE_NANOS;
    try {
      JSONObject holdings =
          JsonRequestCache.await(
              cache.request(
                  "holdings:" + code, ReferencePolicy.DAY_MS, () -> sources.holdings(code)),
              deadline);
      if (holdings.has("error")) {
        return failure("持仓暂不可用：" + holdings.optString("error"), now);
      }
      String date = nav == null ? "" : nav.optString("date");
      if (needsEligibilityCheck(nav, date, holdings, now)) {
        return FundReference.calculate(nav, holdings, Collections.emptyMap(), now);
      }
      JSONArray stocks = holdings.getJSONArray("stocks");
      Map<String, FutureTask<JSONObject>> jobs = new LinkedHashMap<>();
      List<String> secids = new ArrayList<>();
      scheduleReturns(stocks, date, jobs, secids);
      FutureTask<JSONObject> latest = scheduleLatestBatch(secids);
      Map<String, JSONObject> samples = collectReturns(jobs, deadline);
      updateLatest(samples, secids, latest, deadline);
      return FundReference.calculate(nav, holdings, samples, clock.getAsLong());
    } catch (Exception unavailable) {
      return failure("持仓参考暂不可用", now);
    }
  }

  private boolean needsEligibilityCheck(JSONObject nav, String date, JSONObject holdings, long now)
      throws Exception {
    return nav == null
        || date.isEmpty()
        || ChronoUnit.DAYS.between(LocalDate.parse(date), MarketTime.today(now))
            > ReferencePolicy.MAX_NAV_AGE_DAYS
        || ChronoUnit.DAYS.between(
                LocalDate.parse(holdings.getString("reportDate")), MarketTime.today(now))
            > ReferencePolicy.MAX_REPORT_AGE_DAYS;
  }

  private void scheduleReturns(
      JSONArray stocks, String date, Map<String, FutureTask<JSONObject>> jobs, List<String> secids)
      throws Exception {
    for (int i = 0; i < stocks.length(); i++) {
      JSONObject stock = stocks.getJSONObject(i);
      String secid = stock.getString("secid"), currency = stock.optString("currency");
      if (currency.isEmpty()) continue;
      if (!secid.startsWith("Y:")) secids.add(secid);
      jobs.put(
          secid,
          cache.request(
              "stock:" + secid + ":" + date,
              ReferencePolicy.PRICE_CACHE_MS,
              () -> sources.stockReturn(stock, date)));
      if (!currency.equals("CNY") && !jobs.containsKey(currency)) {
        String symbol = HoldingMarkets.fxSymbol(currency);
        if (symbol != null) {
          jobs.put(
              currency,
              cache.request(
                  "fx:" + symbol + ":" + date,
                  ReferencePolicy.PRICE_CACHE_MS,
                  () -> sources.exchangeReturn(symbol, date)));
        }
      }
    }
  }

  private FutureTask<JSONObject> scheduleLatestBatch(List<String> secids) {
    if (secids.isEmpty()) return null;
    Collections.sort(secids);
    String list = String.join(",", secids);
    return cache.request(
        "quotes:" + list, ReferencePolicy.PRICE_CACHE_MS, () -> sources.latestBatch(list));
  }

  private Map<String, JSONObject> collectReturns(
      Map<String, FutureTask<JSONObject>> jobs, long deadline) throws Exception {
    Map<String, JSONObject> samples = new LinkedHashMap<>();
    for (Map.Entry<String, FutureTask<JSONObject>> entry : jobs.entrySet()) {
      try {
        samples.put(entry.getKey(), JsonRequestCache.await(entry.getValue(), deadline));
      } catch (Exception unavailable) {
        samples.put(entry.getKey(), new JSONObject().put("error", "查询超时或暂不可用"));
      }
    }
    return samples;
  }

  private void updateLatest(
      Map<String, JSONObject> samples,
      List<String> secids,
      FutureTask<JSONObject> job,
      long deadline) {
    if (job == null) return;
    try {
      JSONObject quotes = JsonRequestCache.await(job, deadline);
      Map<String, JSONObject> latest =
          PriceHistoryParser.latestBatch(quotes.getString("raw"), clock.getAsLong());
      for (String secid : secids) {
        JSONObject history = samples.get(secid);
        if (history != null && !history.has("error")) {
          samples.put(
              secid,
              PriceHistoryParser.withLatest(history, latest.get(secid), secid, clock.getAsLong()));
        }
      }
    } catch (Exception optionalLatestUnavailable) {
      // Keep dated daily references when the optional latest batch fails.
    }
  }

  private static JSONObject failure(String reason, long now) {
    try {
      return new JSONObject().put("reason", reason).put("checkedAt", now);
    } catch (JSONException invalidResult) {
      return new JSONObject();
    }
  }
}
