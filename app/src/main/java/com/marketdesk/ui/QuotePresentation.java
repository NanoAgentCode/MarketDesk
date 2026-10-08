package com.marketdesk.ui;

import com.marketdesk.FundMarketHours;
import com.marketdesk.FundTypes;
import com.marketdesk.data.MarketTime;
import java.util.*;
import org.json.*;

/** Pure quote/error formatting, shared by UI and refresh failure reporting. */
public final class QuotePresentation {
  private QuotePresentation() {}

  public static String details(
      String code, JSONObject quote, boolean failed, String error, String fundMarket, long now) {
    String status =
        !quote.has("price")
            ? error
            : quote.optString("source")
                + (historical(quote, now) ? " · 历史行情" : "")
                + (failed
                    ? "\n" + error + "，保留缓存"
                    : now - quote.optLong("received") > 20 * 60000 ? " · 缓存待更新" : "");
    if (code.startsWith("F:")) {
      String type = FundTypes.type(code);
      return "基金代码："
          + code
          + "\n\n数据状态："
          + status
          + FundPresentation.details(quote)
          + "\n\n"
          + (type.isEmpty() ? "" : "基金类型：" + type + "\n")
          + FundMarketHours.description(fundMarket, now);
    }
    String price =
        quote.has("price")
            ? String.format(Locale.CHINA, "%,.3f", quote.optDouble("price"))
            : "暂无行情";
    String change =
        quote.has("change")
            ? String.format(Locale.CHINA, "%+.2f%%", quote.optDouble("change"))
            : "—";
    return "行情代码："
        + code
        + "\n\n最新价："
        + price
        + (quote.optString("unit").isEmpty() ? "" : " " + quote.optString("unit"))
        + "\n涨跌幅："
        + change
        + "\n行情时间："
        + quote.optString("time", "—")
        + "（北京时间）\n\n数据状态："
        + status
        + "\n\n涨跌幅以上一交易日收盘价为基准。行情可能延迟。"
        + FundPresentation.details(quote)
        + "\n\n交叉验证："
        + verificationDetails(quote)
        + "\n\n时间相近或同日历史数据才比较；价格容差0.1%，涨跌幅容差0.05个百分点。免费来源可能共享上游，不等于交易所独立确认。";
  }

  public static String errorText(Exception e) {
    if (e.getMessage() != null && e.getMessage().startsWith("全部行情源不可用")) return e.getMessage();
    if (e instanceof java.net.SocketTimeoutException) return "网络超时，请稍后刷新";
    if (e instanceof java.net.UnknownHostException) return "无法连接行情源，请检查网络";
    if (e instanceof javax.net.ssl.SSLException) return "行情源连接中断，请稍后刷新";
    if (e instanceof org.json.JSONException || e instanceof IllegalArgumentException)
      return "行情源数据缺失或格式变化";
    if (e.getMessage() != null && e.getMessage().startsWith("HTTP "))
      return "行情源返回 " + e.getMessage();
    return "行情获取失败，请稍后刷新";
  }

  public static boolean historical(JSONObject q, long now) {
    long time = q.optLong("dataTime", 0);
    return time > 0 && now - time > 24 * 60 * 60 * 1000L;
  }

  public static String verificationDetails(JSONObject q) {
    if (!q.has("verificationLabel")) return "尚未核验，刷新后显示";
    StringBuilder out = new StringBuilder(q.optString("verificationLabel"));
    if (q.optLong("verifiedAt") > 0)
      out.append("\n核验时间：").append(MarketTime.beijing(q.optLong("verifiedAt")));
    JSONArray samples = q.optJSONArray("verificationSamples");
    if (samples != null)
      for (int i = 0; i < samples.length(); i++) {
        JSONObject sample = samples.optJSONObject(i);
        if (sample != null)
          out.append(
              String.format(
                  Locale.CHINA,
                  "\n%s：%.3f / %+.2f%% / %s",
                  sample.optString("source"),
                  sample.optDouble("price"),
                  sample.optDouble("change"),
                  sample.optString("time")));
      }
    JSONArray failures = q.optJSONArray("verificationFailures");
    if (failures != null)
      for (int i = 0; i < failures.length(); i++) {
        JSONObject error = failures.optJSONObject(i);
        if (error != null)
          out.append("\n")
              .append(error.optString("source"))
              .append("：")
              .append(error.optString("error"));
      }
    return out.toString();
  }

  public static String line(JSONObject q, String code, boolean failed, String error, long now) {
    if (!q.has("price")) return "暂无行情 · " + code + "\n" + error;
    boolean stale = now - q.optLong("received") > 20 * 60 * 1000;
    return String.format(
        Locale.CHINA,
        "%.3f  %+.2f%%\n%s %s · %s%s%s",
        q.optDouble("price"),
        q.optDouble("change"),
        q.optString("unit"),
        q.optString("time"),
        q.optString("source"),
        historical(q, now) ? " · 历史行情" : "",
        failed ? " · " + error + "，缓存" : stale ? " · 缓存待更新" : "");
  }
}
