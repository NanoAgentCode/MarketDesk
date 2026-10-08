package com.marketdesk.data;

import java.util.*;
import org.json.*;

/** Explicit exchange/currency mapping; unknown markets remain unsupported. */
public final class HoldingMarkets {
  private HoldingMarkets() {}

  public static void apply(JSONObject stock, String market, String symbol) throws Exception {
    if ((market.equals("0") || market.equals("1")) && symbol.matches("[0-9]{6}"))
      stock.put("currency", "CNY").put("yahoo", symbol + (market.equals("1") ? ".SS" : ".SZ"));
    else if (market.equals("116") && symbol.matches("[0-9]{5}"))
      stock
          .put("currency", "HKD")
          .put("yahoo", String.format(Locale.ROOT, "%04d", Integer.parseInt(symbol)) + ".HK");
    else if (Arrays.asList("105", "106", "107").contains(market)
        && symbol.matches("[A-Za-z][A-Za-z0-9.-]{0,14}"))
      stock.put("currency", "USD").put("yahoo", symbol.replace('.', '-'));
    else if (market.isEmpty() || market.equals("--")) {
      // These issuer codes were verified; an unknown six-digit code is never assumed to be Korean.
      String name = stock.getString("name"), yahoo = null, currency = null;
      if (symbol.equals("000660")
          && (name.equalsIgnoreCase("SK海力士") || name.equalsIgnoreCase("SK HYNIX"))) {
        yahoo = "000660.KS";
        currency = "KRW";
      } else if (symbol.equals("005930")
          && (name.equals("三星电子") || name.equalsIgnoreCase("SAMSUNG ELECTRONICS"))) {
        yahoo = "005930.KS";
        currency = "KRW";
      } else if (symbol.equalsIgnoreCase("285A")
          && (name.equalsIgnoreCase("KIOXIA") || name.equals("铠侠"))) {
        yahoo = "285A.T";
        currency = "JPY";
      }
      if (yahoo != null)
        stock.put("currency", currency).put("yahoo", yahoo).put("secid", "Y:" + yahoo);
    }
  }

  public static String fxSymbol(String currency) {
    switch (currency) {
      case "USD":
        return "CNY=X";
      case "HKD":
        return "HKDCNY=X";
      case "KRW":
        return "KRWCNY=X";
      case "JPY":
        return "JPYCNY=X";
      default:
        return null;
    }
  }
}
