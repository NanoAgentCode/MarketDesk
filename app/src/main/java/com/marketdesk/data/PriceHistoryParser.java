package com.marketdesk.data;

import com.marketdesk.FundValuation;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.json.*;

/** Parses adjusted historical returns and dated latest quotes, without performing HTTP requests. */
public final class PriceHistoryParser {
  private PriceHistoryParser() {}

  public static JSONObject eastmoneyHistory(String raw, String secid, String navDate, long now)
      throws Exception {
    JSONObject root = new JSONObject(raw);
    if (root.optInt("rc", 0) != 0 || root.isNull("data")) throw new IOException("历史行情不可用");
    JSONObject data = root.getJSONObject("data");
    int dot = secid.indexOf('.');
    if (!data.getString("code").equalsIgnoreCase(secid.substring(dot + 1))
        || data.getInt("market") != Integer.parseInt(secid.substring(0, dot)))
      throw new IOException("历史行情代码或市场不匹配");
    JSONArray rows = data.getJSONArray("klines");
    LocalDate nav = LocalDate.parse(navDate), base = null, last = null;
    double basePrice = 0, lastPrice = 0;
    for (int i = 0; i < rows.length(); i++) {
      String[] fields = rows.getString(i).split(",", -1);
      if (fields.length < 3) continue;
      LocalDate date = LocalDate.parse(fields[0]);
      double close = Double.parseDouble(fields[2]);
      if (!FundValuation.positive(close)) continue;
      if (!date.isAfter(nav) && (base == null || date.isAfter(base))) {
        base = date;
        basePrice = close;
      }
      if (!date.isAfter(MarketTime.today(now)) && (last == null || date.isAfter(last))) {
        last = date;
        lastPrice = close;
      }
    }
    ReferencePolicy.validatePeriod(base, last, navDate, now);
    return new JSONObject()
        .put("ratio", lastPrice / basePrice)
        .put("basePrice", basePrice)
        .put("latestPrice", lastPrice)
        .put("priceFactor", 1)
        .put("navDate", navDate)
        .put("baseDate", base.toString())
        .put("date", last.toString())
        .put("time", last.atStartOfDay(MarketTime.stockZone(secid)).toInstant().toEpochMilli())
        .put("kind", "daily")
        .put("source", "东方财富 · 前复权日线参考");
  }

  public static JSONObject yahooHistory(
      String raw, String symbol, String navDate, String currency, long now) throws Exception {
    JSONObject chart = new JSONObject(raw).getJSONObject("chart");
    if (!chart.isNull("error")) throw new IOException("历史行情返回失败");
    JSONObject result = chart.getJSONArray("result").getJSONObject(0),
        meta = result.getJSONObject("meta");
    if (!symbol.equalsIgnoreCase(meta.getString("symbol"))
        || !currency.equals(meta.getString("currency"))) throw new IOException("历史行情代码或币种不匹配");
    ZoneId zone = ZoneId.of(meta.getString("exchangeTimezoneName"));
    JSONArray times = result.getJSONArray("timestamp"),
        closes =
            result
                .getJSONObject("indicators")
                .getJSONArray("quote")
                .getJSONObject(0)
                .getJSONArray("close");
    JSONArray adjRows = result.getJSONObject("indicators").optJSONArray("adjclose"),
        adjusted =
            adjRows != null && adjRows.length() > 0
                ? adjRows.getJSONObject(0).optJSONArray("adjclose")
                : null;
    LocalDate nav = LocalDate.parse(navDate), base = null, last = null;
    double basePrice = 0, lastPrice = 0, lastRaw = 0;
    long lastTime = 0;
    for (int i = 0; i < times.length() && i < closes.length(); i++) {
      long time = times.optLong(i, 0) * 1000;
      double close = closes.optDouble(i, Double.NaN),
          price = adjusted == null ? close : adjusted.optDouble(i, Double.NaN);
      if (time <= 0
          || time > now + ReferencePolicy.FUTURE_TOLERANCE_MS
          || !FundValuation.positive(close)
          || !FundValuation.positive(price)) continue;
      LocalDate date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate();
      if (!date.isAfter(nav) && (base == null || date.isAfter(base))) {
        base = date;
        basePrice = price;
      }
      if (time >= lastTime) {
        last = date;
        lastPrice = price;
        lastRaw = close;
        lastTime = time;
      }
    }
    double factor = lastRaw > 0 ? lastPrice / lastRaw : 1;
    double live = meta.optDouble("regularMarketPrice", Double.NaN);
    long liveTime = meta.optLong("regularMarketTime") * 1000;
    boolean liveUsed = false;
    if (lastRaw > 0
        && FundValuation.positive(live)
        && liveTime >= lastTime
        && liveTime <= now + ReferencePolicy.FUTURE_TOLERANCE_MS) {
      lastPrice = live * lastPrice / lastRaw;
      lastTime = liveTime;
      last = Instant.ofEpochMilli(liveTime).atZone(zone).toLocalDate();
      liveUsed = true;
    }
    ReferencePolicy.validatePeriod(base, last, navDate, now);
    return new JSONObject()
        .put("ratio", lastPrice / basePrice)
        .put("basePrice", basePrice)
        .put("latestPrice", lastPrice)
        .put("priceFactor", factor)
        .put("navDate", navDate)
        .put("baseDate", base.toString())
        .put("date", last.toString())
        .put("time", lastTime)
        .put("kind", liveUsed ? "quote" : "daily")
        .put("source", "Yahoo · 历史及最新参考");
  }

  public static Map<String, JSONObject> latestBatch(String raw, long now) throws Exception {
    JSONObject root = new JSONObject(raw);
    if (root.optInt("rc", 0) != 0 || root.isNull("data")) throw new IOException("持仓行情暂不可用");
    JSONArray rows = root.getJSONObject("data").getJSONArray("diff");
    Map<String, JSONObject> out = new HashMap<>();
    for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.getJSONObject(i);
      double price = row.optDouble("f2", Double.NaN);
      long time = row.optLong("f124") * 1000;
      if (!FundValuation.positive(price)
          || time <= 0
          || time > now + ReferencePolicy.FUTURE_TOLERANCE_MS
          || now - time > ReferencePolicy.MAX_TIMESTAMP_AGE_DAYS * ReferencePolicy.DAY_MS) continue;
      String secid = row.getInt("f13") + "." + row.getString("f12");
      out.put(secid, new JSONObject().put("price", price).put("time", time));
    }
    return out;
  }

  public static JSONObject withLatest(JSONObject history, JSONObject latest, String secid, long now)
      throws Exception {
    JSONObject out = new JSONObject(history.toString());
    if (latest == null || out.has("error")) return out;
    long time = latest.getLong("time");
    LocalDate date = Instant.ofEpochMilli(time).atZone(MarketTime.stockZone(secid)).toLocalDate();
    if (time < out.getLong("time") || date.isBefore(LocalDate.parse(out.getString("date"))))
      return out;
    ReferencePolicy.validatePeriod(
        LocalDate.parse(out.getString("baseDate")), date, out.getString("navDate"), now);
    double price = latest.getDouble("price") * out.optDouble("priceFactor", 1);
    if (!FundValuation.positive(price)) return out;
    return out.put("ratio", price / out.getDouble("basePrice"))
        .put("latestPrice", price)
        .put("time", time)
        .put("date", date.toString())
        .put("kind", "quote")
        .put("source", out.optString("source") + "＋东方财富延迟行情");
  }
}
