package com.marketdesk;

import android.content.Context;
import com.marketdesk.data.*;
import com.marketdesk.ui.QuotePresentation;
import java.util.List;
import org.json.JSONObject;

/**
 * Android entry points. Network, repositories, batch refresh and formatting have separate owners.
 */
public final class Quotes {
  private Quotes() {}

  public static final String DEFAULTS = WatchlistCodec.DEFAULTS;

  static String beijing(long millis) {
    return MarketTime.beijing(millis);
  }

  public static String watch(Context context) {
    return QuoteStore.watch(context);
  }

  public static List<String[]> items(Context context) {
    return QuoteStore.items(context);
  }

  public static void validate(String text) {
    WatchlistCodec.validate(text);
  }

  static String get(String url) throws Exception {
    return MarketServices.http().get(url);
  }

  static String get(String url, String charset) throws Exception {
    return MarketServices.http().get(url, charset);
  }

  static JSONObject eastmoney(String secid) throws Exception {
    return MarketServices.source().eastmoney(secid);
  }

  static JSONObject finishEastmoney(JSONObject result) throws Exception {
    return MarketServices.source().finishEastmoney(result);
  }

  static JSONObject fetchRaw(String code) throws Exception {
    return MarketServices.source().fetch(code);
  }

  static JSONObject fetch(String code) throws Exception {
    return MarketServices.quotes().fetch(code);
  }

  public static String errorText(Exception error) {
    return QuotePresentation.errorText(error);
  }

  public static boolean historical(JSONObject quote) {
    return QuotePresentation.historical(quote, System.currentTimeMillis());
  }

  public static String verificationDetails(JSONObject quote) {
    return QuotePresentation.verificationDetails(quote);
  }

  public static synchronized void refresh(Context context) {
    QuoteRefreshBatch batch =
        new QuoteRefreshBatch(
            MarketServices.quotes()::fetch,
            (code, error) ->
                android.util.Log.w("MarketDesk", "Quote refresh failed: " + code, error));
    QuoteStore.publish(context, batch.fetch(items(context)), System.currentTimeMillis());
    MarketWidget.render(context);
  }

  public static JSONObject cached(Context context, String code) {
    return QuoteStore.cached(context, code);
  }

  public static String line(Context context, String code) {
    JSONObject quote = cached(context, code);
    boolean failed = QuoteStore.preferences(context).contains("error:" + code);
    String error =
        QuoteStore.preferences(context)
            .getString("error:" + code, quote.has("price") ? "" : "等待刷新");
    return QuotePresentation.line(quote, code, failed, error, System.currentTimeMillis());
  }
}
