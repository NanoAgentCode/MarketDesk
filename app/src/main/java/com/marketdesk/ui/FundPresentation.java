package com.marketdesk.ui;

import com.marketdesk.data.MarketTime;
import java.time.*;
import java.util.*;
import org.json.*;

/** Pure formatting: no requests, Android Context or mutation of cached metrics. */
public final class FundPresentation {
  private FundPresentation() {}

  public static String summary(JSONObject reference) {
    if (reference == null) return "";
    StringBuilder out = new StringBuilder("\n\n持仓参考");
    if (reference.has("referenceNav"))
      out.append(String.format(Locale.CHINA, "\n参考净值：%.4f", reference.optDouble("referenceNav")));
    if (reference.has("contribution"))
      out.append(
          String.format(
              Locale.CHINA,
              "\n已匹配持仓累计贡献：%+.2f 个百分点\n基准净值日期：%s",
              reference.optDouble("contribution"),
              reference.optString("navDate")));
    if (reference.has("disclosedWeight"))
      out.append(
          String.format(
              Locale.CHINA,
              "\n披露 / 可计算权重：%.2f%% / %.2f%%",
              reference.optDouble("disclosedWeight"),
              reference.optDouble("matchedWeight")));
    if (reference.has("reportDate"))
      out.append("\n持仓报告：").append(reference.optString("reportDate"));
    if (reference.has("contribution")) {
      Set<String> dates = new TreeSet<>();
      JSONArray rows = reference.optJSONArray("stocks");
      if (rows != null)
        for (int i = 0; i < rows.length(); i++) {
          JSONObject row = rows.optJSONObject(i);
          if (row != null && row.has("contribution")) {
            dates.add(row.optString("date"));
            if (row.has("fxDate")) dates.add(row.optString("fxDate"));
          }
        }
      out.append("\n价格 / 汇率日期：").append(String.join("、", dates));
    }
    if (reference.has("reason")) out.append("\n").append(reference.optString("reason"));
    if (reference.has("referenceNav")) out.append("\n披露权重固定，未披露仓位假设不变；汇率为市场参考价。不是平台估值或正式净值。");
    else if (reference.has("contribution")) out.append("\n仅为已匹配部分的累计贡献，不是基金当日涨跌幅。");
    return out.toString();
  }

  public static String stockDetails(JSONObject reference) {
    StringBuilder out =
        new StringBuilder(
            "持仓报告："
                + reference.optString("reportDate")
                + "\n基准净值日期："
                + reference.optString("navDate")
                + "\n权重为历史披露占基金净值比例，可能已调仓。\n");
    JSONArray rows = reference.optJSONArray("stocks");
    if (rows == null) return out.append("暂无持仓明细").toString();
    for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.optJSONObject(i);
      if (row == null) continue;
      out.append(
          String.format(
              Locale.CHINA,
              "\n%s（%s）· %.2f%%",
              row.optString("name"),
              row.optString("code"),
              row.optDouble("weight")));
      if (!row.has("contribution")) {
        out.append("\n").append(row.optString("error", "暂不可计算"));
        continue;
      }
      out.append(
          String.format(
              Locale.CHINA,
              "\n贡献 %+.3f 个百分点 · 人民币口径\n价格日期 %s → %s",
              row.optDouble("contribution"),
              row.optString("baseDate"),
              row.optString("date")));
      if (row.optString("kind").equals("daily")) out.append("（日线参考）");
      else
        out.append(" · 北京时间 ")
            .append(
                Instant.ofEpochMilli(row.optLong("time"))
                    .atZone(ZoneId.of("Asia/Shanghai"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")));
      if (row.has("fxDate"))
        out.append(
            String.format(
                Locale.CHINA,
                "\n%s兑人民币 %s → %s（%+.2f%%）",
                row.optString("currency"),
                row.optString("fxBaseDate"),
                row.optString("fxDate"),
                row.optDouble("fxChange")));
      out.append("\n").append(row.optString("source"));
    }
    return out.toString();
  }

  public static String details(JSONObject quote) {
    JSONObject values = quote.optJSONObject("fundMetrics");
    if (values == null) return "";
    StringBuilder out = new StringBuilder("\n\n基金净值与估值");
    JSONObject iopv = values.optJSONObject("iopv");
    if (values.optString("type").equals("etf")) {
      if (iopv == null) out.append("\nIOPV：暂不可用");
      else {
        out.append(String.format(Locale.CHINA, "\nIOPV参考净值：%.4f", iopv.optDouble("value")));
        long time = iopv.optLong("packageTime");
        out.append("\n行情包时间：").append(time > 0 ? MarketTime.beijing(time) : "未提供");
        if (iopv.has("premium"))
          out.append(
              String.format(
                  Locale.CHINA,
                  "\n折溢价：%+.2f%%\n计算所用同包成交价：%.3f",
                  iopv.optDouble("premium"),
                  iopv.optDouble("quotePrice")));
        out.append("\n")
            .append(iopv.optString("source"))
            .append("\nIOPV独立更新时间未提供；该源可能延迟，不能认作保证实时。");
      }
    }
    JSONObject estimate = values.optJSONObject("estimate");
    if (estimate == null) out.append("\n盘中估算净值：暂未提供");
    else {
      out.append(String.format(Locale.CHINA, "\n盘中估算净值：%.4f", estimate.optDouble("value")));
      if (estimate.has("change"))
        out.append(String.format(Locale.CHINA, "（%+.2f%%）", estimate.optDouble("change")));
      out.append("\n估值时间：").append(estimate.optString("time")).append("\n第三方估算，不是IOPV或正式净值。");
    }
    JSONObject nav = values.optJSONObject("nav");
    if (nav == null) out.append("\n已公布净值：暂未提供");
    else
      out.append(
          String.format(
              Locale.CHINA,
              "\n已公布净值：%.4f\n净值日期：%s",
              nav.optDouble("value"),
              nav.optString("date")));
    out.append(summary(values.optJSONObject("holdingsReference")));
    out.append(
        values.optString("type").equals("etf")
            ? "\n日期早于当前交易日时为历史数据；折溢价仅使用同包成交价和IOPV。"
            : "\n主列表显示平台估值或已公布净值；持仓参考单独列示。");
    return out.toString();
  }
}
