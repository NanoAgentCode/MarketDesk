package com.marketdesk;

import com.marketdesk.data.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.json.*;

/** Public holdings and price history, retaining market dates instead of request timestamps. */
public final class FundHoldings {
  static final long DAY = ReferencePolicy.DAY_MS;

  static LocalDate today(long now) {
    return MarketTime.today(now);
  }

  public static JSONObject parse(String raw, String code, long now) throws Exception {
    if (!code.matches("[0-9]{6}")) throw new IOException("基金代码无效");
    JSONObject root = new JSONObject(raw);
    if (!root.optBoolean("Success", false)) throw new IOException("持仓接口返回失败");
    String report = root.getString("Expansion");
    LocalDate reportDate = LocalDate.parse(report);
    if (reportDate.isAfter(today(now))) throw new IOException("持仓报告日期异常");
    JSONArray input = root.getJSONObject("Datas").optJSONArray("fundStocks"),
        stocks = new JSONArray();
    Set<String> seen = new HashSet<>();
    double total = 0;
    if (input != null)
      for (int i = 0; i < input.length(); i++) {
        JSONObject row = input.getJSONObject(i);
        double weight = row.getDouble("JZBL");
        if (!Double.isFinite(weight) || weight < 0 || weight > 100) throw new IOException("持仓权重无效");
        if (weight == 0) continue;
        String market = row.optString("NEWTEXCH", ""), symbol = row.getString("GPDM").trim();
        if (market.equals("116") && symbol.matches("[0-9]{1,5}"))
          symbol = String.format(Locale.ROOT, "%05d", Integer.parseInt(symbol));
        String secid = market + "." + symbol;
        if (!seen.add(secid)) throw new IOException("持仓重复");
        JSONObject stock =
            new JSONObject()
                .put("code", symbol)
                .put("name", row.optString("GPJC", symbol))
                .put("weight", weight)
                .put("secid", secid);
        HoldingMarkets.apply(stock, market, symbol);
        stocks.put(stock);
        total += weight;
      }
    if (total > 100.01) throw new IOException("披露权重超过基金净值");
    return new JSONObject()
        .put("code", code)
        .put("reportDate", report)
        .put("disclosedWeight", total)
        .put("stocks", stocks)
        .put("checkedAt", now);
  }

  static String fxSymbol(String currency) {
    return HoldingMarkets.fxSymbol(currency);
  }

  static void validatePeriod(LocalDate base, LocalDate target, String navDate, long now)
      throws Exception {
    ReferencePolicy.validatePeriod(base, target, navDate, now);
  }

  public static JSONObject eastmoneyHistory(String raw, String secid, String navDate, long now)
      throws Exception {
    return PriceHistoryParser.eastmoneyHistory(raw, secid, navDate, now);
  }

  public static JSONObject yahooHistory(
      String raw, String symbol, String navDate, String currency, long now) throws Exception {
    return PriceHistoryParser.yahooHistory(raw, symbol, navDate, currency, now);
  }

  public static Map<String, JSONObject> latestBatch(String raw, long now) throws Exception {
    return PriceHistoryParser.latestBatch(raw, now);
  }

  public static JSONObject withLatest(JSONObject history, JSONObject latest, String secid, long now)
      throws Exception {
    return PriceHistoryParser.withLatest(history, latest, secid, now);
  }
}
