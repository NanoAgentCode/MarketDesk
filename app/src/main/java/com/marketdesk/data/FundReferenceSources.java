package com.marketdesk.data;

import com.marketdesk.FundHoldings;
import com.marketdesk.network.HttpTransport;
import com.marketdesk.network.MarketUrls;
import java.util.function.LongSupplier;
import org.json.JSONObject;

/** Source fallback is separate from the reference workflow and its request cache. */
public final class FundReferenceSources {
  private final HttpTransport http;
  private final LongSupplier clock;

  public FundReferenceSources(HttpTransport http, LongSupplier clock) {
    this.http = http;
    this.clock = clock;
  }

  public JSONObject holdings(String code) throws Exception {
    return FundHoldings.parse(http.get(MarketUrls.holdings(code)), code, clock.getAsLong());
  }

  public JSONObject stockReturn(JSONObject stock, String date) throws Exception {
    String secid = stock.getString("secid");
    if (!secid.startsWith("Y:")) {
      try {
        return PriceHistoryParser.eastmoneyHistory(
            http.get(MarketUrls.stockHistory(secid, date, clock.getAsLong())),
            secid,
            date,
            clock.getAsLong());
      } catch (Exception firstSourceFailed) {
        // Preserve the same-symbol Yahoo fallback when the historical source is unavailable.
      }
    }
    String symbol = stock.getString("yahoo");
    return PriceHistoryParser.yahooHistory(
        http.get(MarketUrls.yahooHistory(symbol, date, clock.getAsLong())),
        symbol,
        date,
        stock.getString("currency"),
        clock.getAsLong());
  }

  public JSONObject exchangeReturn(String symbol, String date) throws Exception {
    return PriceHistoryParser.yahooHistory(
        http.get(MarketUrls.yahooHistory(symbol, date, clock.getAsLong())),
        symbol,
        date,
        "CNY",
        clock.getAsLong());
  }

  public JSONObject latestBatch(String secids) throws Exception {
    return new JSONObject().put("raw", http.get(MarketUrls.latestBatch(secids)));
  }
}
