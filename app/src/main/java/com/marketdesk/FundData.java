package com.marketdesk;

import com.marketdesk.data.MarketServices;
import com.marketdesk.ui.FundPresentation;
import org.json.JSONObject;

/** Compatibility entry points; retrieval and presentation are maintained independently. */
public final class FundData {
  private FundData() {}

  public static JSONObject valuation(String code) throws Exception {
    return MarketServices.funds().valuation(code);
  }

  public static JSONObject monitored(String code) throws Exception {
    return MarketServices.funds().monitored(code);
  }

  public static JSONObject etf(String secid) {
    return MarketServices.funds().etf(secid);
  }

  public static String details(JSONObject quote) {
    return FundPresentation.details(quote);
  }
}
