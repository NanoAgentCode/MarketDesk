package com.marketdesk.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Android storage adapter. Preference names and batch publication semantics remain compatible. */
public final class QuoteStore {
  private QuoteStore() {}

  public static SharedPreferences preferences(Context context) {
    return context.getSharedPreferences("market", 0);
  }

  public static String watch(Context context) {
    return preferences(context).getString("watch", WatchlistCodec.DEFAULTS);
  }

  public static List<String[]> items(Context context) {
    return WatchlistCodec.items(watch(context));
  }

  public static JSONObject cached(Context context, String code) {
    try {
      return new JSONObject(preferences(context).getString(code, "{}"));
    } catch (Exception unavailable) {
      return new JSONObject();
    }
  }

  public static void publish(Context context, QuoteRefreshBatch.Result result, long now) {
    SharedPreferences.Editor update = preferences(context).edit();
    for (Map.Entry<String, String> entry : result.quotes.entrySet()) {
      update.putString(entry.getKey(), entry.getValue()).remove("error:" + entry.getKey());
    }
    for (Map.Entry<String, String> entry : result.errors.entrySet()) {
      update.putString("error:" + entry.getKey(), entry.getValue());
    }
    update.putLong("attempt", now).apply();
  }
}
