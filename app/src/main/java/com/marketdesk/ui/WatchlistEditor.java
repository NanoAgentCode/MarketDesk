package com.marketdesk.ui;

import static com.marketdesk.ui.ViewTheme.*;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.marketdesk.QuoteCode;
import com.marketdesk.R;
import com.marketdesk.WatchlistTable;
import java.util.*;
import java.util.function.*;

/** Owns the editor draft, focus controls and restoration lifecycle. */
public final class WatchlistEditor {
  private final Activity activity;
  private final ViewFactory views;
  private final Supplier<String> watchlist;
  private final Consumer<String> saveWatch;
  private Dialog editorDialog;
  private WatchlistTable editorDraft;

  public WatchlistEditor(
      Activity activity,
      ViewFactory views,
      Supplier<String> watchlist,
      Consumer<String> saveWatch) {
    this.activity = activity;
    this.views = views;
    this.watchlist = watchlist;
    this.saveWatch = saveWatch;
  }

  public boolean isOpen() {
    return editorDialog != null;
  }

  public void dismiss() {
    if (editorDialog != null) editorDialog.dismiss();
  }

  public void restore(Bundle saved) {
    if (saved == null || !saved.containsKey("draftNames")) return;
    editorDraft = new WatchlistTable("");
    ArrayList<String> names = saved.getStringArrayList("draftNames"),
        codes = saved.getStringArrayList("draftCodes");
    if (names != null && codes != null)
      for (int i = 0; i < Math.min(names.size(), codes.size()); i++) {
        editorDraft.add();
        WatchlistTable.Row row = editorDraft.rows().get(i);
        row.name = names.get(i);
        row.code = codes.get(i);
      }
    show();
  }

  public void saveState(Bundle out) {
    if (editorDialog == null) return;
    captureDraft();
    ArrayList<String> names = new ArrayList<>(), codes = new ArrayList<>();
    for (WatchlistTable.Row row : editorDraft.rows()) {
      names.add(row.name);
      codes.add(row.code);
    }
    out.putStringArrayList("draftNames", names);
    out.putStringArrayList("draftCodes", codes);
  }

  private final List<EditCell> editorCells = new ArrayList<>();

  private static final class EditCell {
    final WatchlistTable.Row row;
    final EditText name, code;
    final Spinner source;

    EditCell(WatchlistTable.Row row, EditText name, EditText code, Spinner source) {
      this.row = row;
      this.name = name;
      this.code = code;
      this.source = source;
    }
  }

  public void show() {
    if (editorDialog != null) return;
    if (editorDraft == null) editorDraft = new WatchlistTable(watchlist.get());
    Dialog dialog = new Dialog(activity, R.style.AppTheme);
    editorDialog = dialog;
    LinearLayout root = views.column();
    root.setBackgroundColor(BG);
    root.setPadding(views.dp(12), views.dp(20), views.dp(12), views.dp(12));
    root.setFocusableInTouchMode(true);
    root.requestFocus();
    views.label(root, "编辑自选表格", 24, TEXT, true);
    views.label(root, "直接点击名称或代码修改。最多20项，保存后同步到桌面。", 12, MUTED, false);
    views.label(root, "来源下拉选择 Y / E / F，代码无需输入前缀。", 11, ACCENT, false);
    views.gap(root, 12);
    ScrollView vertical = new ScrollView(activity);
    vertical.setFillViewport(true);
    HorizontalScrollView horizontal = new HorizontalScrollView(activity);
    horizontal.setFillViewport(true);
    horizontal.setHorizontalScrollBarEnabled(true);
    LinearLayout table = views.column();
    horizontal.addView(table);
    vertical.addView(horizontal);
    root.addView(vertical, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView error = views.label(root, "", 12, RED, false);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    views.action(
        root,
        "＋  添加一行",
        false,
        () -> {
          captureDraft();
          try {
            editorDraft.add();
            renderEditor(table, error);
            vertical.post(() -> vertical.fullScroll(View.FOCUS_DOWN));
          } catch (IllegalArgumentException e) {
            error.setText(e.getMessage());
          }
        });
    LinearLayout bottom = views.row();
    views.halfAction(bottom, "取消", false, dialog::cancel);
    views.halfAction(
        bottom,
        "保存表格",
        true,
        () -> {
          captureDraft();
          try {
            String value = editorDraft.encode();
            saveWatch.accept(value);
            dialog.dismiss();
          } catch (IllegalArgumentException e) {
            error.setText(e.getMessage());
          }
        });
    root.addView(bottom);
    TextView examples = views.label(root, "查看代码示例", 12, ACCENT, false);
    examples.setGravity(Gravity.CENTER);
    examples.setPadding(0, views.dp(12), 0, views.dp(12));
    examples.setOnClickListener(
        v ->
            new AlertDialog.Builder(activity)
                .setTitle("代码示例")
                .setMessage(
                    "港股：Y:0700.HK\n"
                        + "恒生科技：Y:HSTECH.HK\n"
                        + "港股通创新药：E:2.931250\n"
                        + "美股：Y:AAPL / Y:QQQ\n"
                        + "A股：E:1.600519 / E:0.000001\n"
                        + "黄金ETF：E:1.518880\n"
                        + "场外基金估算：F:000001")
                .setPositiveButton("知道了", null)
                .show());
    dialog.setContentView(root);
    dialog.setOnDismissListener(
        d -> {
          editorDialog = null;
          editorDraft = null;
          editorCells.clear();
        });
    renderEditor(table, error);
    dialog.show();
    Window w = dialog.getWindow();
    if (w != null) {
      w.setLayout(-1, -1);
      w.setSoftInputMode(
          WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
              | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
      w.getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
      root.setOnApplyWindowInsetsListener(
          (v, insets) -> {
            v.setPadding(
                views.dp(12),
                insets.getSystemWindowInsetTop() + views.dp(12),
                views.dp(12),
                insets.getSystemWindowInsetBottom() + views.dp(12));
            return insets;
          });
      root.requestApplyInsets();
    }
  }

  private void captureDraft() {
    for (EditCell cell : editorCells) {
      cell.row.name = cell.name.getText().toString();
      cell.row.code =
          QuoteCode.join(cell.source.getSelectedItemPosition(), cell.code.getText().toString());
    }
  }

  private void renderEditor(LinearLayout table, TextView error) {
    table.removeAllViews();
    editorCells.clear();
    error.setText("");
    LinearLayout header = views.row();
    header.setBackgroundColor(LINE);
    views.tableCell(header, "#", 28, Gravity.CENTER, MUTED, true);
    views.tableCell(header, "名称", 104, Gravity.START, MUTED, true);
    views.tableCell(header, "来源 / 代码", 120, Gravity.START, MUTED, true);
    views.tableCell(header, "操作", 96, Gravity.CENTER, MUTED, true);
    table.addView(header);
    int index = 0;
    for (WatchlistTable.Row draft : editorDraft.rows()) {
      final int position = index++;
      LinearLayout line = views.row();
      line.setBackgroundColor(position % 2 == 0 ? CARD : 0xff18263b);
      line.setMinimumHeight(views.dp(96));
      views.tableCell(line, String.valueOf(position + 1), 28, Gravity.CENTER, MUTED, false);
      EditText name = views.editorInput(draft.name, "名称", false),
          code = views.editorInput(QuoteCode.body(draft.code), "0700.HK", true);
      code.setMinHeight(views.dp(48));
      Spinner source = new Spinner(activity, Spinner.MODE_DROPDOWN);
      source.setDropDownWidth(views.dp(220));
      ArrayAdapter<String> choices =
          new ArrayAdapter<String>(
              activity,
              android.R.layout.simple_spinner_item,
              new String[] {"Y · Yahoo", "E · 东财", "F · 基金"}) {
            @Override
            public View getView(int p, View old, ViewGroup parent) {
              TextView t = (TextView) super.getView(p, old, parent);
              t.setTextSize(12);
              t.setTextColor(ACCENT);
              return t;
            }

            @Override
            public View getDropDownView(int p, View old, ViewGroup parent) {
              TextView t = (TextView) super.getDropDownView(p, old, parent);
              t.setText(new String[] {"Y · Yahoo 行情", "E · 东方财富行情", "F · 场外基金估算"}[p]);
              t.setTextColor(TEXT);
              t.setMinHeight(views.dp(48));
              return t;
            }
          };
      choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      source.setAdapter(choices);
      source.setSelection(QuoteCode.source(draft.code));
      source.setContentDescription("第" + (position + 1) + "行行情来源");
      source.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int p, long id) {
              code.setHint(new String[] {"0700.HK / ^IXIC", "1.561380", "000001"}[p]);
            }

            public void onNothingSelected(AdapterView<?> parent) {}
          });
      LinearLayout sourceAndCode = views.column();
      sourceAndCode.addView(source, new LinearLayout.LayoutParams(-1, views.dp(48)));
      sourceAndCode.addView(code);
      name.setContentDescription("第" + (position + 1) + "行名称");
      code.setContentDescription("第" + (position + 1) + "行不含前缀的行情代码");
      line.addView(name, new LinearLayout.LayoutParams(views.dp(104), -2));
      line.addView(sourceAndCode, new LinearLayout.LayoutParams(views.dp(120), -2));
      editorCells.add(new EditCell(draft, name, code, source));
      LinearLayout controls = views.row(), reorder = views.column();
      editorControl(
          reorder,
          "↑",
          "上移第" + (position + 1) + "行",
          position > 0,
          () -> {
            captureDraft();
            editorDraft.move(position, -1);
            renderEditor(table, error);
          });
      editorControl(
          reorder,
          "↓",
          "下移第" + (position + 1) + "行",
          position < editorDraft.rows().size() - 1,
          () -> {
            captureDraft();
            editorDraft.move(position, 1);
            renderEditor(table, error);
          });
      controls.addView(reorder);
      editorControl(
          controls,
          "×",
          "删除第" + (position + 1) + "行",
          true,
          () -> {
            captureDraft();
            editorDraft.remove(position);
            renderEditor(table, error);
          });
      line.addView(controls, new LinearLayout.LayoutParams(views.dp(96), -2));
      table.addView(line);
      views.divider(table);
    }
    if (editorDraft.rows().isEmpty()) views.label(table, "暂无自选，点击下方「添加一行」。", 14, MUTED, false);
  }

  private void editorControl(
      LinearLayout parent, String value, String description, boolean enabled, Runnable task) {
    TextView control =
        views.label(null, value, 20, enabled ? (value.equals("×") ? RED : ACCENT) : MUTED, true);
    control.setGravity(Gravity.CENTER);
    control.setContentDescription(description);
    control.setEnabled(enabled);
    control.setAlpha(enabled ? 1 : 0.3f);
    control.setMinHeight(views.dp(48));
    control.setOnClickListener(v -> task.run());
    parent.addView(control, new LinearLayout.LayoutParams(views.dp(48), views.dp(48)));
  }
}
