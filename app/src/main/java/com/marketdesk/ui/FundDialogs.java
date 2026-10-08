package com.marketdesk.ui;

import static com.marketdesk.ui.ViewTheme.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Handler;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.marketdesk.*;
import com.marketdesk.data.QuoteStore;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Owns the existing fund query/add flow and keeps asynchronous callbacks lifecycle-aware. */
public final class FundDialogs {
  private final Activity activity;
  private final Handler handler;
  private final ViewFactory views;
  private final Consumer<String> saveWatch;

  public FundDialogs(
      Activity activity, Handler handler, ViewFactory views, Consumer<String> saveWatch) {
    this.activity = activity;
    this.handler = handler;
    this.views = views;
    this.saveWatch = saveWatch;
  }

  private SharedPreferences prefs() {
    return QuoteStore.preferences(activity);
  }

  public void showQuery() {
    String[] markets = {"A股", "港股", "美股", "多市场 / 其他"};
    new AlertDialog.Builder(activity)
        .setTitle("主要投资市场（仅备注）")
        .setItems(markets, (dialog, index) -> showCodeInput(markets[index]))
        .show();
  }

  private void showCodeInput(String market) {
    LinearLayout box = views.column();
    box.setPadding(views.dp(20), views.dp(8), views.dp(20), views.dp(8));
    views.label(
        box, FundMarketHours.description(market, System.currentTimeMillis()), 12, MUTED, false);
    EditText code = views.editorInput("", "六位基金代码，例如005827", true);
    code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    box.addView(code);
    TextView state = views.label(box, "查询平台估值、净值及披露持仓参考。", 12, MUTED, false);
    AlertDialog input =
        new AlertDialog.Builder(activity)
            .setTitle("查询基金估值可用状态")
            .setView(box)
            .setPositiveButton("查询", null)
            .setNegativeButton("取消", null)
            .create();
    input.setOnShowListener(
        dialog ->
            input
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    view -> query(code.getText().toString().trim(), market, input, state)));
    input.show();
  }

  private void query(String code, String market, AlertDialog input, TextView state) {
    if (!code.matches("[0-9]{6}")) {
      state.setText("请填写六位基金代码");
      return;
    }
    input.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    state.setText("正在查询净值与持仓参考…");
    new Thread(
            () -> {
              try {
                JSONObject metrics = FundData.monitored(code);
                JSONObject quote = FundValuation.quote(metrics, System.currentTimeMillis());
                handler.post(
                    () -> {
                      if (!isActive(input)) return;
                      input.dismiss();
                      showResult(code, market, metrics, quote);
                    });
              } catch (Exception error) {
                handler.post(
                    () -> {
                      if (!isActive(input)) return;
                      input.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                      state.setText("查询失败：" + Quotes.errorText(error));
                    });
              }
            },
            "fund-availability")
        .start();
  }

  private boolean isActive(AlertDialog input) {
    return !activity.isFinishing() && !activity.isDestroyed() && input.isShowing();
  }

  private void showResult(String code, String market, JSONObject metrics, JSONObject quote) {
    String name = metrics.optString("name", code);
    String status = metrics.has("estimate") ? "平台有估算值，请先检查估值时间。" : "主列表显示已公布净值；持仓参考单独列示。";
    new AlertDialog.Builder(activity)
        .setTitle(name)
        .setMessage(
            status
                + FundPresentation.details(quote)
                + "\n\n"
                + FundMarketHours.description(market, System.currentTimeMillis()))
        .setPositiveButton("加入监测", (dialog, which) -> add(code, name, market))
        .setNegativeButton("取消", null)
        .show();
  }

  private void add(String code, String name, String market) {
    String full = "F:" + code;
    for (String[] item : Quotes.items(activity)) {
      if (item[1].equals(full)) {
        prefs().edit().putString("fundMarket:" + full, market).apply();
        RefreshWorker.afterEdit(activity);
        views.toast("已在自选中，市场备注已更新");
        return;
      }
    }
    try {
      String next = Quotes.watch(activity).trim() + "\n" + name + "|" + full;
      Quotes.validate(next);
      prefs().edit().putString("fundMarket:" + full, market).apply();
      saveWatch.accept(next);
    } catch (IllegalArgumentException error) {
      views.toast(error.getMessage());
    }
  }

  public void showRequested() {
    StringBuilder summary = new StringBuilder();
    for (RequestedFunds.Fund fund : RequestedFunds.FUNDS) {
      summary
          .append(fund.name)
          .append("（")
          .append(fund.code)
          .append("）\n")
          .append(fund.type)
          .append("\n\n");
    }
    summary.append("加入后按F基金代码监测，已有项不重复添加。平台估值与净值在主列表显示，披露持仓参考见「更多」。");
    new AlertDialog.Builder(activity)
        .setTitle("加入指定的5只基金")
        .setMessage(summary)
        .setPositiveButton("全部加入", (dialog, which) -> addRequested())
        .setNegativeButton("取消", null)
        .show();
  }

  private void addRequested() {
    try {
      String value = RequestedFunds.append(Quotes.watch(activity));
      prefs()
          .edit()
          .putString("fundMarket:F:017436", "美股")
          .putString("fundMarket:F:013308", "港股")
          .putString("fundMarket:F:023638", "A股")
          .putString("fundMarket:F:100055", "多市场 / 其他")
          .putString("fundMarket:F:021030", "港股")
          .apply();
      saveWatch.accept(value);
    } catch (IllegalArgumentException error) {
      views.toast("加入失败：" + error.getMessage() + "，现有自选已保留");
    }
  }
}
