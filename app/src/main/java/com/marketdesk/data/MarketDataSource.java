package com.marketdesk.data;

import com.marketdesk.EastmoneyParser;
import com.marketdesk.FundValuation;
import com.marketdesk.TencentParser;
import com.marketdesk.network.HttpTransport;
import com.marketdesk.network.MarketUrls;
import java.io.IOException;
import java.time.Instant;
import java.util.function.LongSupplier;
import org.json.JSONObject;

/** Reads one provider. Cross-provider verification and persistence belong to other modules. */
public final class MarketDataSource {
  @FunctionalInterface
  public interface FundSource {
    JSONObject monitored(String code) throws Exception;
  }

  private final HttpTransport http;
  private final LongSupplier clock;
  private final FundSource funds;

  public MarketDataSource(HttpTransport http, LongSupplier clock, FundSource funds) {
    this.http = http;
    this.clock = clock;
    this.funds = funds;
  }

  public JSONObject fetch(String code) throws Exception {
    if (code.startsWith("T:")) {
      JSONObject quote =
          TencentParser.parse(
              http.get(MarketUrls.tencent(code.substring(2)), "GB18030"), code.substring(2));
      return quote
          .put("time", MarketTime.beijing(quote.getLong("dataTime")))
          .put("received", clock.getAsLong());
    }
    if (code.startsWith("E:")) return eastmoney(code.substring(2));
    if (!code.startsWith("Y:")) {
      return FundValuation.quote(funds.monitored(code.substring(2)), clock.getAsLong());
    }
    JSONObject metadata =
        new JSONObject(http.get(MarketUrls.yahooQuote(code.substring(2))))
            .getJSONObject("chart")
            .getJSONArray("result")
            .getJSONObject(0)
            .getJSONObject("meta");
    if (!metadata.getString("symbol").equalsIgnoreCase(code.substring(2))) {
      throw new IOException("返回代码不匹配");
    }
    double price = metadata.getDouble("regularMarketPrice");
    double previous = metadata.getDouble("chartPreviousClose");
    // previousClose is the previous trading session; chartPreviousClose may be the range baseline.
    if (metadata.has("previousClose")) previous = metadata.getDouble("previousClose");
    if (previous <= 0) throw new IOException("昨收无效");
    double change = (price / previous - 1) * 100;
    if (!Double.isFinite(price) || !Double.isFinite(change)) throw new IOException("无效数据");
    long time = metadata.getLong("regularMarketTime") * 1000;
    return new JSONObject()
        .put("price", price)
        .put("change", change)
        .put("dataTime", time)
        .put("time", MarketTime.beijing(time))
        .put("unit", metadata.optString("currency", ""))
        .put("source", "Yahoo · 可能延迟")
        .put("received", clock.getAsLong());
  }

  public JSONObject eastmoney(String secid) throws Exception {
    try {
      return finishEastmoney(
          EastmoneyParser.latest(http.get(MarketUrls.eastmoneyLatest(secid)), secid));
    } catch (Exception latestError) {
      try {
        return finishEastmoney(
            EastmoneyParser.trends(http.get(MarketUrls.eastmoneyTrends(secid)), secid));
      } catch (Exception trendsError) {
        try {
          return finishEastmoney(
              EastmoneyParser.daily(http.get(MarketUrls.eastmoneyDaily(secid)), secid));
        } catch (Exception dailyError) {
          dailyError.addSuppressed(latestError);
          dailyError.addSuppressed(trendsError);
          throw dailyError;
        }
      }
    }
  }

  public JSONObject finishEastmoney(JSONObject result) throws Exception {
    long time = result.getLong("dataTime");
    String display =
        result.optString("kind").equals("daily")
            ? Instant.ofEpochMilli(time).atZone(MarketTime.BEIJING).toLocalDate() + " 日线"
            : MarketTime.beijing(time);
    return result.put("time", display).put("received", clock.getAsLong());
  }
}
