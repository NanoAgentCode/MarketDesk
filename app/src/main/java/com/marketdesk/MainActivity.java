package com.marketdesk;

import static com.marketdesk.ui.ViewTheme.*;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.marketdesk.ui.*;
import java.util.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
  private ViewFactory views;
  private WatchlistEditor watchlistEditor;
  private FundDialogs fundDialogs;
  private QuoteDetailsDialog quoteDetails;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private LinearLayout content, tabs;
  private TextView pinStatus, liveStatus;
  private int page;
  private boolean resumed, helpShown;
  private long requestToken, requestedAt;
  private int[] priorWidgetIds = new int[0];
  private String quoteSnapshot = "";
  private ScrollView pageScroll;
  private FrameLayout widgetPreviewHost;
  private TextView widgetPreviewInfo;
  private int previewWidgetId;
  private String widgetPreviewSignature = "";
  private final Runnable syncDisplayedQuotes =
      () -> {
        if (!resumed) return;
        if (page == 0 && !watchlistEditor.isOpen()) renderQuotes();
        if (page == 1) refreshWidgetPreview(false);
      };
  private final SharedPreferences.OnSharedPreferenceChangeListener quoteListener =
      (preferences, key) -> {
        if ("attempt".equals(key) || "watch".equals(key)) {
          handler.removeCallbacks(syncDisplayedQuotes);
          handler.post(syncDisplayedQuotes);
        }
      };
  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (!resumed) return;
          if (page == 0 && !watchlistEditor.isOpen() && !quoteSnapshot.equals(snapshot()))
            renderQuotes();
          if (page == 1) {
            refreshWidgetPreview(false);
            updatePinStatus();
          }
          if (page == 2 && liveStatus != null) liveStatus.setText(liveDescription());
          handler.postDelayed(this, 2500);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    views = new ViewFactory(this);
    watchlistEditor = new WatchlistEditor(this, views, () -> Quotes.watch(this), this::saveWatch);
    fundDialogs = new FundDialogs(this, handler, views, this::saveWatch);
    quoteDetails = new QuoteDetailsDialog(this);
    if (saved != null) {
      page = saved.getInt("page");
      requestToken = saved.getLong("pinToken");
      requestedAt = saved.getLong("pinAt");
      helpShown = saved.getBoolean("pinHelp");
      int[] ids = saved.getIntArray("priorIds");
      if (ids != null) priorWidgetIds = ids;
    }
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    LinearLayout root = views.column();
    root.setBackgroundColor(BG);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              views.dp(20),
              insets.getSystemWindowInsetTop() + views.dp(12),
              views.dp(20),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    setContentView(root);
    root.requestApplyInsets();
    LinearLayout header = views.row();
    header.setPadding(0, views.dp(8), 0, views.dp(18));
    LinearLayout brand = views.column();
    views.label(brand, "MARKET DESK", 10, ACCENT, true);
    views.label(brand, "行情桌面", 26, TEXT, true);
    header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    TextView badge = views.label(null, "自选行情", 11, MUTED, false);
    badge.setPadding(views.dp(12), views.dp(8), views.dp(12), views.dp(8));
    badge.setBackground(views.bg(CARD, 12));
    header.addView(badge);
    root.addView(header);
    tabs = views.row();
    tabs.setPadding(views.dp(4), views.dp(4), views.dp(4), views.dp(4));
    tabs.setBackground(views.bg(CARD, 16));
    root.addView(tabs);
    pageScroll = new ScrollView(this);
    pageScroll.setFillViewport(true);
    pageScroll.setClipToPadding(false);
    pageScroll.setPadding(0, views.dp(20), 0, views.dp(20));
    content = views.column();
    pageScroll.addView(content);
    root.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    switchPage(page);
    RefreshWorker.schedule(this);
    RefreshWorker.now(this);
    watchlistEditor.restore(saved);
  }

  private void switchPage(int selected) {
    if (pageScroll != null) pageScroll.scrollTo(0, 0);
    page = selected;
    tabs.removeAllViews();
    String[] names = {"自选", "桌面", "设置"};
    for (int i = 0; i < names.length; i++) {
      final int target = i;
      TextView tab = views.label(null, names[i], 14, i == page ? BG : MUTED, i == page);
      tab.setGravity(Gravity.CENTER);
      tab.setPadding(views.dp(4), views.dp(12), views.dp(4), views.dp(12));
      if (i == page) tab.setBackground(views.bg(ACCENT, 12));
      tabs.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
      tab.setOnClickListener(v -> switchPage(target));
    }
    pinStatus = null;
    liveStatus = null;
    widgetPreviewHost = null;
    widgetPreviewInfo = null;
    if (page == 0) renderQuotes();
    else if (page == 1) renderWidget();
    else renderSettings();
  }

  private void renderQuotes() {
    quoteSnapshot = snapshot();
    int previousScroll = pageScroll.getScrollY();
    content.removeAllViews();
    List<String[]> items = Quotes.items(this);
    int ready = 0;
    for (String[] item : items) if (Quotes.cached(this, item[1]).has("price")) ready++;
    views.label(content, "我的自选", 20, TEXT, true);
    views.label(
        content,
        items.size()
            + " 项关注 · "
            + ready
            + " 项已有行情 · "
            + (LiveService.running ? LiveService.interval(this) + "秒间隔盯盘" : "后台约15分钟更新"),
        12,
        MUTED,
        false);
    LinearLayout tools = views.row();
    views.halfAction(
        tools,
        "↻  刷新",
        true,
        () -> {
          RefreshWorker.now(this);
          views.toast("已安排刷新，有网络时执行");
        });
    views.halfAction(tools, "编辑表格", false, () -> editWatchlist());
    content.addView(tools);
    long attempt = prefs().getLong("attempt", 0);
    views.label(
        content,
        attempt == 0 ? "正在获取行情…" : "上次刷新尝试 " + Quotes.beijing(attempt) + " · 北京时间",
        11,
        MUTED,
        false);
    views.gap(content, 8);
    LinearLayout table = views.column();
    table.setBackground(views.bg(CARD, 16));
    LinearLayout header = views.row();
    header.setBackgroundColor(LINE);
    views.quoteCell(header, "名称 / 代码", 1.5f, Gravity.START, MUTED, true);
    views.quoteCell(header, "涨跌幅", 0.9f, Gravity.END, MUTED, true);
    views.quoteCell(header, "行情时间", 1.1f, Gravity.CENTER, MUTED, true);
    views.tableCell(header, "更多", 48, Gravity.CENTER, MUTED, true);
    table.addView(header);
    int index = 0;
    for (String[] item : items) {
      JSONObject q = Quotes.cached(this, item[1]);
      LinearLayout line = views.row();
      line.setMinimumHeight(views.dp(64));
      line.setBackgroundColor(index++ % 2 == 0 ? CARD : 0xff18263b);
      LinearLayout name = views.column();
      name.setPadding(views.dp(6), views.dp(8), views.dp(4), views.dp(8));
      TextView title = views.label(name, item[0], 13, TEXT, true);
      title.setMaxLines(3);
      title.setEllipsize(android.text.TextUtils.TruncateAt.END);
      title.setPadding(0, 0, 0, 0);
      title.setLineSpacing(views.dp(1), 1);
      TextView code = views.label(name, item[1], 10, MUTED, false);
      code.setMaxLines(2);
      code.setEllipsize(android.text.TextUtils.TruncateAt.END);
      code.setPadding(0, views.dp(3), 0, 0);
      code.setLineSpacing(0, 1);
      line.addView(name, new LinearLayout.LayoutParams(0, -2, 1.5f));
      views.quoteCell(
          line,
          q.has("change") ? String.format(Locale.CHINA, "%+.2f%%", q.optDouble("change")) : "—",
          0.9f,
          Gravity.END,
          q.has("change") ? changeColor(q) : MUTED,
          true);
      views.quoteCell(
          line,
          q.has("price") ? q.optString("time").replace(" ", "\n") : "—",
          1.1f,
          Gravity.CENTER,
          MUTED,
          false);
      TextView more = views.label(null, "更多", 12, ACCENT, true);
      more.setGravity(Gravity.CENTER);
      more.setMinHeight(views.dp(48));
      more.setContentDescription("查看" + item[0] + "的最新价和数据状态");
      more.setOnClickListener(v -> showQuoteDetails(item));
      line.addView(more, new LinearLayout.LayoutParams(views.dp(48), views.dp(64)));
      line.setOnClickListener(v -> showQuoteDetails(item));
      table.addView(line);
      views.divider(table);
    }
    content.addView(table, new LinearLayout.LayoutParams(-1, -2));
    views.gap(content, 6);
    views.label(content, "点击「更多」查看价格、IOPV、基金净值与估值\n红涨绿跌 · 各数据时间独立标注", 11, MUTED, false);
    pageScroll.post(
        () -> {
          if (page == 0) pageScroll.scrollTo(0, previousScroll);
        });
  }

  private void showQuoteDetails(String[] item) {
    quoteDetails.show(item);
  }

  private void renderWidget() {
    content.removeAllViews();
    views.label(content, "把关注，放在桌面", 24, TEXT, true);
    views.label(content, "随手看涨跌，不用每次打开 App。", 13, MUTED, false);
    views.gap(content, 18);
    widgetPreviewInfo = views.label(content, "", 12, MUTED, false);
    widgetPreviewInfo.setOnClickListener(v -> choosePreviewWidget());
    widgetPreviewHost = new FrameLayout(this);
    content.addView(widgetPreviewHost, new LinearLayout.LayoutParams(-1, views.dp(280)));
    refreshWidgetPreview(true);
    views.label(content, "预览与小部件共用布局及行情，较大尺寸会缩放。点击上方可切换小部件。", 11, MUTED, false);
    views.gap(content, 20);
    views.action(content, "编辑名称 / 代码", false, () -> editWatchlist());
    views.action(content, "＋  添加到桌面", true, () -> requestWidget());
    pinStatus = views.label(content, "", 12, MUTED, false);
    updatePinStatus();
    views.gap(content, 16);
    LinearLayout help = views.card();
    views.label(help, "没有弹出添加窗口？", 16, TEXT, true);
    views.label(help, "桌面由系统处理添加请求。可以直接在手机桌面手动选择小部件。", 12, MUTED, false);
    views.gap(help, 8);
    views.label(
        help, "01  回到桌面，长按空白位置或双指捏合\n02  打开「小部件 / 添加小部件」\n03  找到「行情桌面」，拖到空白区域", 13, TEXT, false);
    views.label(help, "HyperOS 入口随版本不同；部分版本需进入「全部小部件 / 安卓小部件」列表。", 11, MUTED, false);
    views.action(help, "查看添加指引", false, () -> manualHelp());
    content.addView(help);
  }

  private void choosePreviewWidget() {
    int[] ids = widgetIds();
    if (ids.length == 0) {
      views.toast("添加小部件后，将按实际尺寸预览");
      return;
    }
    String[] names = new String[ids.length];
    for (int i = 0; i < ids.length; i++) {
      int[] size =
          MarketWidget.dimensions(
              this, AppWidgetManager.getInstance(this).getAppWidgetOptions(ids[i]));
      names[i] = "小部件 #" + ids[i] + " · " + size[0] + " × " + size[1];
    }
    new AlertDialog.Builder(this)
        .setTitle("选择桌面小部件")
        .setItems(
            names,
            (d, index) -> {
              previewWidgetId = ids[index];
              refreshWidgetPreview(true);
            })
        .setNegativeButton("取消", null)
        .show();
  }

  private void refreshWidgetPreview(boolean force) {
    if (widgetPreviewHost == null || widgetPreviewInfo == null) return;
    int[] ids = widgetIds();
    boolean found = false;
    for (int id : ids) if (id == previewWidgetId) found = true;
    if (!found) previewWidgetId = ids.length > 0 ? ids[0] : 0;
    Bundle options =
        previewWidgetId == 0
            ? new Bundle()
            : AppWidgetManager.getInstance(this).getAppWidgetOptions(previewWidgetId);
    int[] size = MarketWidget.dimensions(this, options);
    int width = Math.max(180, size[0]), height = Math.max(120, size[1]);
    String signature = snapshot() + "/" + previewWidgetId + "/" + width + "/" + height;
    if (!force && signature.equals(widgetPreviewSignature)) return;
    widgetPreviewSignature = signature;
    MarketWidget.DisplaySnapshot batch = MarketWidget.snapshot(this);
    MarketWidget.render(this, batch);
    int count = WidgetPresentation.rows(batch.values, height, batch.time).size();
    widgetPreviewInfo.setText(
        (previewWidgetId == 0 ? "默认尺寸预览（尚未添加）" : "小部件 #" + previewWidgetId)
            + " · "
            + width
            + " × "
            + height
            + " · 显示 "
            + count
            + " 项");
    widgetPreviewHost.removeAllViews();
    View rendered = MarketWidget.views(this, height, batch).apply(this, widgetPreviewHost);
    int available = getResources().getDisplayMetrics().widthPixels - views.dp(40);
    float scale = Math.min(1f, (float) Math.max(views.dp(180), available) / views.dp(width));
    rendered.setPivotX(0);
    rendered.setPivotY(0);
    rendered.setScaleX(scale);
    rendered.setScaleY(scale);
    widgetPreviewHost.addView(
        rendered, new FrameLayout.LayoutParams(views.dp(width), views.dp(height)));
    widgetPreviewHost.getLayoutParams().height = Math.round(views.dp(height) * scale);
    widgetPreviewHost.requestLayout();
    View refresh = rendered.findViewById(R.id.refresh);
    refresh.setOnClickListener(
        v -> {
          RefreshWorker.now(this);
          views.toast("已安排刷新，将同步更新预览和小部件");
        });
    rendered.findViewById(R.id.title).setOnClickListener(v -> switchPage(0));
    for (int target : new int[] {R.id.widget_root, R.id.rows, R.id.status})
      rendered.findViewById(target).setOnClickListener(v -> switchPage(0));
    ViewGroup previewRows = (ViewGroup) rendered.findViewById(R.id.rows);
    for (int i = 0; i < previewRows.getChildCount(); i++) {
      View line = previewRows.getChildAt(i);
      line.setOnClickListener(v -> switchPage(0));
      for (int target : new int[] {R.id.name, R.id.meta, R.id.price, R.id.change})
        line.findViewById(target).setOnClickListener(v -> switchPage(0));
    }
  }

  private void requestWidget() {
    AppWidgetManager manager = AppWidgetManager.getInstance(this);
    if (!manager.isRequestPinAppWidgetSupported()) {
      manualHelp();
      pinStatus.setText("当前桌面不支持 App 内添加，请使用手动添加。");
      return;
    }
    if (requestToken != 0 && System.currentTimeMillis() - requestedAt < 10000) {
      views.toast("已请求系统桌面，请稍候或使用手动添加");
      return;
    }
    priorWidgetIds = widgetIds();
    requestToken = System.currentTimeMillis();
    requestedAt = requestToken;
    helpShown = false;
    Intent done =
        new Intent(this, WidgetPinnedReceiver.class)
            .setAction("com.marketdesk.PINNED")
            .setData(Uri.parse("marketdesk://pin/" + requestToken))
            .putExtra("request_token", requestToken);
    PendingIntent callback =
        PendingIntent.getBroadcast(
            this, 30, done, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    try {
      boolean accepted =
          manager.requestPinAppWidget(new ComponentName(this, MarketWidget.class), null, callback);
      if (!accepted) {
        requestToken = 0;
        pinStatus.setText("桌面未接受添加请求，请手动添加。");
        manualHelp();
        return;
      }
      pinStatus.setText("已请求系统桌面，请在弹出的窗口中确认添加。");
      views.toast("已请求桌面，未弹窗时可使用下方添加指引");
      handler.postDelayed(this::updatePinStatus, 10000);
    } catch (RuntimeException error) {
      requestToken = 0;
      pinStatus.setText("未能唤起系统桌面，请使用手动添加。");
      manualHelp();
    }
  }

  private void updatePinStatus() {
    if (pinStatus == null) return;
    int[] current = widgetIds();
    if (requestToken != 0) {
      boolean success =
          prefs().getLong("pin_success", 0) == requestToken
              || PinFeedback.hasNewWidget(priorWidgetIds, current);
      PinFeedback.State state =
          PinFeedback.resolve(true, success, System.currentTimeMillis() - requestedAt);
      if (state == PinFeedback.State.CONFIRMED) {
        requestToken = 0;
        pinStatus.setText("已添加到桌面 · 当前有 " + current.length + " 个小部件");
        views.toast("桌面小部件已添加");
        MarketWidget.render(this);
        return;
      }
      if (state == PinFeedback.State.WAITING) {
        pinStatus.setText("等待桌面确认… 若未弹窗，可使用下方手动添加指引。");
        return;
      }
      pinStatus.setText("尚未收到添加确认。可回桌面检查，或按指引手动添加。");
      if (resumed && hasWindowFocus() && !helpShown) {
        helpShown = true;
        manualHelp();
      }
    } else
      pinStatus.setText(
          current.length > 0
              ? "已检测到 " + current.length + " 个桌面小部件，可再添加一个。"
              : "添加后可以拉伸大小，点击标题打开 App。");
  }

  private int[] widgetIds() {
    return AppWidgetManager.getInstance(this)
        .getAppWidgetIds(new ComponentName(this, MarketWidget.class));
  }

  private void manualHelp() {
    new AlertDialog.Builder(this)
        .setTitle("手动添加桌面小部件")
        .setMessage(
            "1. 回到手机桌面，长按空白处或双指捏合。\n\n"
                + "2. 点击“小部件 / 添加小部件”，找到“行情桌面”。部分 HyperOS 版本需进入“全部小部件 / 安卓小部件”。\n\n"
                + "3. 拖到有足够空间的位置，然后拉伸大小。\n\n"
                + "如果列表里没有：检查是否为主空间安装的 App、是否使用系统桌面；可先重新打开 App，再查看小部件列表。桌面菜单以手机实际显示为准。")
        .setPositiveButton(
            "回到桌面",
            (d, w) -> {
              try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
              } catch (RuntimeException e) {
                views.toast("请按手机 Home 键回到桌面");
              }
            })
        .setNegativeButton("留在 App", null)
        .show();
  }

  private void renderSettings() {
    content.removeAllViews();
    views.label(content, "按你的习惯看行情", 24, TEXT, true);
    views.label(content, "自选、刷新和后台运行，在这里管理。", 13, MUTED, false);
    views.gap(content, 18);
    LinearLayout watch = views.card();
    views.label(watch, "自选管理", 18, TEXT, true);
    views.label(watch, "当前关注 " + Quotes.items(this).size() + " 项，桌面与 App 同步。", 12, MUTED, false);
    views.action(watch, "编辑自选列表", false, () -> editWatchlist());
    views.action(watch, "添加常用标的", false, () -> addPreset());
    content.addView(watch);
    views.gap(content, 14);
    LinearLayout live = views.card();
    views.label(live, "盯盘模式", 18, TEXT, true);
    liveStatus = views.label(live, liveDescription(), 12, MUTED, false);
    views.action(
        live, "刷新间隔：" + LiveService.interval(this) + " 秒（点击修改）", false, () -> chooseInterval());
    views.action(live, "开启盯盘", true, () -> startLive());
    views.action(
        live,
        "结束盯盘",
        false,
        () -> {
          stopService(new Intent(this, LiveService.class));
          liveStatus.setText("已结束盯盘 · 后台约15分钟更新");
        });
    views.label(
        live, "每轮请求结束后等待所选间隔再刷新，App和小部件一起更新。最长2小时，显示常驻通知；越快越耗电，也更容易被行情源限流。", 11, MUTED, false);
    content.addView(live);
    views.gap(content, 14);
    LinearLayout system = views.card();
    views.label(system, "后台与通知", 18, TEXT, true);
    views.label(system, "若 HyperOS 限制后台，可检查自启动、通知和应用省电设置。默认后台刷新约15分钟，系统可能延后。", 12, MUTED, false);
    views.action(
        system,
        "打开应用系统设置",
        false,
        () -> {
          try {
            startActivity(
                new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
          } catch (RuntimeException e) {
            views.toast("请从手机设置中打开行情桌面的应用详情");
          }
        });
    content.addView(system);
    views.gap(content, 16);
    LinearLayout funds = views.card();
    views.label(funds, "基金净值与估值", 18, TEXT, true);
    views.label(
        funds,
        "ETF：更多中查看IOPV、折溢价和净值。\n主动/QDII/联接基金：按六位代码监测。主列表显示平台估值或净值，持仓参考与计算依据放在「更多」。",
        12,
        MUTED,
        false);
    views.action(funds, "加入指定的5只基金", true, () -> addRequestedFunds());
    views.action(funds, "查询其他主动 / 联接基金", false, () -> addActiveFund());
    views.action(funds, "通过表格添加基金 / ETF", false, () -> editWatchlist());
    content.addView(funds);
    views.gap(content, 14);
    views.label(
        content,
        "行情桌面  1.9.0\n免费多源监测：Yahoo / 东方财富 / 腾讯财经\n基金参考见「更多」；行情与汇率最多缓存60秒，持仓缓存一天。",
        11,
        MUTED,
        false);
  }

  private void startLive() {
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 7);
      return;
    }
    try {
      startForegroundService(new Intent(this, LiveService.class));
      views.toast("正在开启盯盘");
    } catch (RuntimeException e) {
      views.toast("系统暂不允许开启盯盘，请稍后再试");
    }
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(code, permissions, grants);
    if (code == 7) {
      if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startLive();
      else
        new AlertDialog.Builder(this)
            .setTitle("通知未开启")
            .setMessage("允许通知后，可以在通知栏看到盯盘运行状态并结束盯盘。普通刷新仍可使用。")
            .setPositiveButton("知道了", null)
            .show();
    }
  }

  private String liveDescription() {
    long end = prefs().getLong("live_until", 0);
    return LiveService.running && end > System.currentTimeMillis()
        ? "盯盘运行中 · "
            + LiveService.interval(this)
            + "秒间隔 · 约 "
            + Math.max(1, (end - System.currentTimeMillis()) / 60000)
            + " 分钟后结束"
        : "未开启 · 后台约15分钟更新";
  }

  private void chooseInterval() {
    int[] seconds = {15, 30, 60, 120};
    String[] options = {"15秒", "30秒（默认）", "60秒", "120秒"};
    int chosen = 1;
    for (int i = 0; i < seconds.length; i++)
      if (seconds[i] == LiveService.interval(this)) chosen = i;
    new AlertDialog.Builder(this)
        .setTitle("盯盘刷新间隔")
        .setSingleChoiceItems(
            options,
            chosen,
            (dialog, index) -> {
              prefs().edit().putInt("live_seconds", seconds[index]).apply();
              if (LiveService.running)
                try {
                  startForegroundService(new Intent(this, LiveService.class));
                } catch (RuntimeException e) {
                  views.toast("通知状态将在下次开启盯盘时更新");
                }
              dialog.dismiss();
              switchPage(page);
              views.toast("已保存，新间隔在后续轮次生效");
            })
        .setNegativeButton("取消", null)
        .show();
  }

  private void addActiveFund() {
    fundDialogs.showQuery();
  }

  private void addRequestedFunds() {
    fundDialogs.showRequested();
  }

  private void editWatchlist() {
    watchlistEditor.show();
  }

  private void addPreset() {
    String[] markets = {"美股代表性指数", "A股代表性指数", "港股代表性指数"};
    String[][] names = {
      {"纳斯达克综合指数", "纳斯达克100指数", "标普500指数", "道琼斯工业指数"},
      {"上证指数", "深证成指", "沪深300指数", "创业板指数"},
      {"恒生指数", "恒生科技指数", "恒生国企指数"}
    };
    String[][] codes = {
      {"Y:^IXIC", "Y:^NDX", "Y:^GSPC", "Y:^DJI"},
      {"E:1.000001", "E:0.399001", "E:1.000300", "E:0.399006"},
      {"Y:^HSI", "Y:HSTECH.HK", "Y:^HSCE"}
    };
    new AlertDialog.Builder(this)
        .setTitle("添加常用标的 · 选择市场")
        .setItems(
            markets,
            (dialog, market) -> {
              Set<String> watched = new HashSet<>();
              for (String[] item : Quotes.items(this))
                watched.addAll(QuoteSources.plan(item[1]).values());
              List<String> availableNames = new ArrayList<>(), availableCodes = new ArrayList<>();
              for (int i = 0; i < codes[market].length; i++)
                if (!watched.contains(codes[market][i])) {
                  availableNames.add(names[market][i]);
                  availableCodes.add(codes[market][i]);
                }
              AlertDialog.Builder options = new AlertDialog.Builder(this).setTitle(markets[market]);
              if (availableCodes.isEmpty()) options.setMessage("该市场的常用指数均已加入自选。");
              else
                options.setItems(
                    availableNames.toArray(new String[0]),
                    (d, index) -> {
                      String code = availableCodes.get(index);
                      for (String[] item : Quotes.items(this))
                        if (QuoteSources.plan(item[1]).containsValue(code)) {
                          views.toast("已在自选列表中");
                          return;
                        }
                      try {
                        saveWatch(
                            Quotes.watch(this).trim()
                                + "\n"
                                + availableNames.get(index)
                                + "|"
                                + code);
                      } catch (IllegalArgumentException e) {
                        views.toast(e.getMessage());
                      }
                    });
              options.setNegativeButton("返回市场", (d, w) -> addPreset()).show();
            })
        .setNegativeButton("取消", null)
        .show();
  }

  private void saveWatch(String value) {
    Quotes.validate(value);
    prefs().edit().putString("watch", value).apply();
    MarketWidget.render(this);
    RefreshWorker.afterEdit(this);
    switchPage(page);
    views.toast("自选已保存，已同步到桌面小部件");
  }

  private int changeColor(JSONObject q) {
    return q.optDouble("change") > 0 ? RED : q.optDouble("change") < 0 ? GREEN : MUTED;
  }

  private SharedPreferences prefs() {
    return com.marketdesk.data.QuoteStore.preferences(this);
  }

  private String snapshot() {
    StringBuilder s = new StringBuilder(Quotes.watch(this));
    s.append(LiveService.running).append(LiveService.interval(this));
    s.append(prefs().getLong("attempt", 0));
    s.append(System.currentTimeMillis() / 60000);
    for (String[] item : Quotes.items(this)) {
      s.append(prefs().getString(item[1], ""));
      s.append(prefs().getString("error:" + item[1], ""));
    }
    return s.toString();
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    prefs().registerOnSharedPreferenceChangeListener(quoteListener);
    MarketWidget.render(this);
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  @Override
  protected void onPause() {
    super.onPause();
    resumed = false;
    prefs().unregisterOnSharedPreferenceChangeListener(quoteListener);
    handler.removeCallbacks(tick);
    handler.removeCallbacks(syncDisplayedQuotes);
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (watchlistEditor != null) watchlistEditor.dismiss();
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putInt("page", page);
    out.putLong("pinToken", requestToken);
    out.putLong("pinAt", requestedAt);
    out.putBoolean("pinHelp", helpShown);
    out.putIntArray("priorIds", priorWidgetIds);
    watchlistEditor.saveState(out);
  }
}
