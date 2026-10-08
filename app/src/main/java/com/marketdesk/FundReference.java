package com.marketdesk;

import com.marketdesk.data.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.json.*;

/** Fixed disclosed weights; the undisclosed portfolio is explicitly assumed unchanged. */
public final class FundReference {
  public static JSONObject calculate(
      JSONObject nav, JSONObject holdings, Map<String, JSONObject> samples, long now)
      throws Exception {
    JSONObject out =
        new JSONObject().put("reportDate", holdings.getString("reportDate")).put("checkedAt", now);
    JSONArray rows = new JSONArray(), stocks = holdings.getJSONArray("stocks");
    out.put("stocks", rows);
    if (nav == null || !FundValuation.positive(nav.optDouble("value")))
      return out.put("reason", "缺少已公布净值，暂不计算参考净值");
    String date = nav.getString("date");
    out.put("navDate", date).put("baseNav", nav.getDouble("value"));
    long navAge = ChronoUnit.DAYS.between(LocalDate.parse(date), MarketTime.today(now)),
        reportAge =
            ChronoUnit.DAYS.between(
                LocalDate.parse(holdings.getString("reportDate")), MarketTime.today(now));
    if (navAge < 0 || navAge > ReferencePolicy.MAX_NAV_AGE_DAYS)
      return out.put("reason", "净值日期过旧或异常，暂不计算");
    if (reportAge < 0 || reportAge > ReferencePolicy.MAX_REPORT_AGE_DAYS)
      return out.put("reason", "持仓报告过旧或异常，暂不计算");
    double disclosed = 0, matched = 0, contribution = 0;
    long earliest = Long.MAX_VALUE, latest = 0;
    for (int i = 0; i < stocks.length(); i++) {
      JSONObject stock = stocks.getJSONObject(i), row = new JSONObject(stock.toString());
      rows.put(row);
      double weight = stock.getDouble("weight");
      if (!Double.isFinite(weight) || weight <= 0 || weight > 100)
        throw new IllegalArgumentException("持仓权重无效");
      disclosed += weight;
      String currency = stock.optString("currency"), secid = stock.getString("secid");
      JSONObject sample = samples.get(secid),
          fx = currency.equals("CNY") ? null : samples.get(currency);
      if (!currency.equals("CNY") && HoldingMarkets.fxSymbol(currency) == null) {
        row.put("error", "暂不支持该市场或币种");
        continue;
      }
      if (!valid(sample, date, now)) {
        row.put("error", sample == null ? "历史价格暂不可用" : sample.optString("error", "历史价格日期不齐"));
        continue;
      }
      if (!currency.equals("CNY") && !valid(fx, date, now)) {
        row.put("error", currency + "汇率或历史日期暂不可用");
        continue;
      }
      double fxRatio = fx == null ? 1 : fx.getDouble("ratio"),
          change = (sample.getDouble("ratio") * fxRatio - 1) * 100,
          part = weight * change / 100;
      if (!Double.isFinite(change) || !Double.isFinite(part)) {
        row.put("error", "价格计算无效");
        continue;
      }
      matched += weight;
      contribution += part;
      long time = sample.getLong("time");
      earliest = Math.min(earliest, time);
      latest = Math.max(latest, time);
      row.put("change", change)
          .put("contribution", part)
          .put("baseDate", sample.getString("baseDate"))
          .put("date", sample.getString("date"))
          .put("time", time)
          .put("kind", sample.optString("kind", "daily"))
          .put("source", sample.optString("source"));
      if (fx != null) {
        row.put("fxChange", (fxRatio - 1) * 100)
            .put("fxDate", fx.getString("date"))
            .put("fxBaseDate", fx.getString("baseDate"))
            .put("fxTime", fx.getLong("time"));
        earliest = Math.min(earliest, fx.getLong("time"));
        latest = Math.max(latest, fx.getLong("time"));
      }
    }
    if (disclosed > 100.01) throw new IllegalArgumentException("持仓权重超过净值");
    out.put("disclosedWeight", disclosed)
        .put("matchedWeight", matched)
        .put("missingWeight", Math.max(0, disclosed - matched))
        .put("unknownWeight", Math.max(0, 100 - disclosed));
    if (matched > 0)
      out.put("contribution", contribution).put("dataTime", earliest).put("latestTime", latest);
    if (disclosed == 0) out.put("reason", "暂无直接股票持仓；不以目标ETF或指数替代");
    else if (disclosed - matched > 0.00001) out.put("reason", "部分行情或汇率缺失，仅显示已匹配持仓贡献");
    else {
      double reference = nav.getDouble("value") * (1 + contribution / 100);
      if (FundValuation.positive(reference)) out.put("referenceNav", reference);
    }
    return out;
  }

  private static boolean valid(JSONObject sample, String date, long now) {
    try {
      if (sample == null
          || !date.equals(sample.optString("navDate"))
          || !FundValuation.positive(sample.optDouble("ratio"))) return false;
      LocalDate base = LocalDate.parse(sample.getString("baseDate")),
          target = LocalDate.parse(sample.getString("date"));
      ReferencePolicy.validatePeriod(base, target, date, now);
      long time = sample.getLong("time");
      return time > 0
          && time <= now + ReferencePolicy.FUTURE_TOLERANCE_MS
          && now - time <= ReferencePolicy.MAX_TIMESTAMP_AGE_DAYS * ReferencePolicy.DAY_MS;
    } catch (Exception e) {
      return false;
    }
  }

  public static String summary(JSONObject reference) {
    return com.marketdesk.ui.FundPresentation.summary(reference);
  }

  public static String stockDetails(JSONObject reference) {
    return com.marketdesk.ui.FundPresentation.stockDetails(reference);
  }
}
