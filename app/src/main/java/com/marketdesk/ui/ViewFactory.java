package com.marketdesk.ui;

import static com.marketdesk.ui.ViewTheme.*;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared native view styling; components remain owned by their Activity. */
public final class ViewFactory {
  private final Context context;

  public ViewFactory(Context context) {
    this.context = context;
  }

  public int dp(float value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  public LinearLayout column() {
    LinearLayout l = new LinearLayout(context);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  public LinearLayout row() {
    LinearLayout l = new LinearLayout(context);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  public GradientDrawable bg(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  public LinearLayout card() {
    LinearLayout l = column();
    l.setPadding(dp(18), dp(16), dp(18), dp(16));
    l.setBackground(bg(CARD, 20));
    return l;
  }

  public TextView label(LinearLayout parent, String value, int size, int color, boolean bold) {
    TextView t = new TextView(context);
    t.setText(value);
    t.setTextColor(color);
    t.setTextSize(size);
    t.setFontFeatureSettings("tnum");
    t.setLineSpacing(dp(3), 1);
    t.setPadding(0, dp(3), 0, dp(3));
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    if (parent != null) parent.addView(t);
    return t;
  }

  public void action(LinearLayout parent, String value, boolean primary, Runnable task) {
    TextView button = label(null, value, 14, primary ? BG : ACCENT, true);
    button.setGravity(Gravity.CENTER);
    button.setMinHeight(dp(48));
    button.setPadding(dp(14), dp(12), dp(14), dp(12));
    button.setBackground(bg(primary ? ACCENT : LINE, 14));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(10);
    parent.addView(button, p);
    button.setOnClickListener(v -> task.run());
  }

  public void halfAction(LinearLayout parent, String value, boolean primary, Runnable task) {
    TextView button = label(null, value, 14, primary ? BG : ACCENT, true);
    button.setGravity(Gravity.CENTER);
    button.setMinHeight(dp(48));
    button.setBackground(bg(primary ? ACCENT : LINE, 12));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.setMargins(dp(3), dp(10), dp(3), dp(10));
    parent.addView(button, p);
    button.setOnClickListener(v -> task.run());
  }

  public void tableCell(
      LinearLayout parent, String value, int width, int gravity, int color, boolean bold) {
    TextView cell = label(null, value, 12, color, bold);
    cell.setGravity(gravity | Gravity.CENTER_VERTICAL);
    cell.setPadding(dp(4), dp(8), dp(4), dp(8));
    cell.setLineSpacing(dp(1), 1);
    cell.setMinHeight(dp(40));
    cell.setMaxLines(3);
    cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
    parent.addView(cell, new LinearLayout.LayoutParams(dp(width), -2));
  }

  public void quoteCell(
      LinearLayout parent, String value, float weight, int gravity, int color, boolean bold) {
    TextView cell = label(null, value, 12, color, bold);
    cell.setGravity(gravity | Gravity.CENTER_VERTICAL);
    cell.setPadding(dp(4), dp(8), dp(4), dp(8));
    cell.setLineSpacing(dp(1), 1);
    cell.setMinHeight(dp(40));
    cell.setMaxLines(3);
    cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
    parent.addView(cell, new LinearLayout.LayoutParams(0, -2, weight));
  }

  public void divider(LinearLayout parent) {
    View line = new View(context);
    line.setBackgroundColor(LINE);
    parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
  }

  public void gap(LinearLayout parent, int height) {
    View v = new View(context);
    parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
  }

  public void toast(String message) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
  }

  public EditText editorInput(String value, String hint, boolean code) {
    EditText input = new EditText(context);
    input.setTextColor(TEXT);
    input.setHintTextColor(MUTED);
    input.setTextSize(13);
    input.setPadding(dp(5), dp(10), dp(5), dp(10));
    input.setMinHeight(dp(64));
    input.setSelectAllOnFocus(false);
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | (code ? android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS : 0));
    input.setSingleLine(code);
    if (!code) {
      input.setMaxLines(3);
      input.setHorizontallyScrolling(false);
    }
    input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
    input.setHint(hint);
    input.setText(value);
    return input;
  }
}
