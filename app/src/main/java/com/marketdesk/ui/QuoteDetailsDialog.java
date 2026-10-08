package com.marketdesk.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import com.marketdesk.*;
import org.json.*;

/** Detail and holdings dialogs do not add navigation or persistent UI state. */
public final class QuoteDetailsDialog {
  private final Activity activity;

  public QuoteDetailsDialog(Activity activity) {
    this.activity = activity;
  }

  private SharedPreferences prefs() {
    return com.marketdesk.data.QuoteStore.preferences(activity);
  }

  public void show(String[] item) {
    JSONObject q = Quotes.cached(activity, item[1]);
    String message =
        QuotePresentation.details(
            item[1],
            q,
            prefs().contains("error:" + item[1]),
            prefs().getString("error:" + item[1], q.has("price") ? "" : "等待行情"),
            prefs().getString("fundMarket:" + item[1], ""),
            System.currentTimeMillis());
    AlertDialog.Builder details =
        new AlertDialog.Builder(activity)
            .setTitle(item[0])
            .setMessage(message)
            .setPositiveButton("关闭", null);
    JSONObject metrics = q.optJSONObject("fundMetrics"),
        reference = metrics == null ? null : metrics.optJSONObject("holdingsReference");
    JSONArray stocks = reference == null ? null : reference.optJSONArray("stocks");
    if (stocks != null && stocks.length() > 0)
      details.setNeutralButton(
          "持仓明细",
          (dialog, which) ->
              new AlertDialog.Builder(activity)
                  .setTitle(item[0] + " · 持仓参考")
                  .setMessage(FundPresentation.stockDetails(reference))
                  .setPositiveButton("关闭", null)
                  .show());
    details.show();
  }
}
