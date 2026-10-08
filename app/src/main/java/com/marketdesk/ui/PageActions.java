package com.marketdesk.ui;

import java.util.*;

/** Existing page actions, kept independent of Android menu presentation and execution. */
public final class PageActions {
  public enum Action {
    REFRESH("刷新", 0),
    EDIT_WATCHLIST("编辑自选列表", 0),
    CHOOSE_PREVIEW("切换预览小部件", 0),
    PIN_WIDGET("添加到桌面", 0),
    WIDGET_HELP("查看添加指引", 0),
    ADD_PRESET("添加常用标的", 0),
    REFRESH_INTERVAL("刷新间隔", 1),
    START_LIVE("开启盯盘", 1),
    STOP_LIVE("结束盯盘", 1),
    SYSTEM_SETTINGS("打开应用系统设置", 2),
    MANAGE_FUNDS("基金管理", 3);

    public final int group;
    private final String title;

    Action(String title, int group) {
      this.title = title;
      this.group = group;
    }

    public int id() {
      return ordinal() + 1;
    }

    public String title(int interval) {
      return this == REFRESH_INTERVAL ? title + "：" + interval + " 秒" : title;
    }

    public String title(int interval, int page) {
      return this == EDIT_WATCHLIST && page == 2 ? "编辑自选 / 基金 / ETF" : title(interval);
    }
  }

  private static final List<Action> WATCHLIST = immutable(Action.REFRESH, Action.EDIT_WATCHLIST);
  private static final List<Action> WIDGET =
      immutable(
          Action.EDIT_WATCHLIST, Action.CHOOSE_PREVIEW, Action.PIN_WIDGET, Action.WIDGET_HELP);
  private static final List<Action> SETTINGS = settings(Action.START_LIVE);
  private static final List<Action> RUNNING_SETTINGS = settings(Action.STOP_LIVE);

  private PageActions() {}

  private static List<Action> immutable(Action... actions) {
    return Collections.unmodifiableList(Arrays.asList(actions));
  }

  private static List<Action> settings(Action live) {
    return immutable(
        Action.EDIT_WATCHLIST,
        Action.ADD_PRESET,
        Action.REFRESH_INTERVAL,
        live,
        Action.SYSTEM_SETTINGS,
        Action.MANAGE_FUNDS);
  }

  public static List<Action> forPage(int page) {
    return forPage(page, false);
  }

  public static List<Action> forPage(int page, boolean liveRunning) {
    switch (page) {
      case 0:
        return WATCHLIST;
      case 1:
        return WIDGET;
      case 2:
        return liveRunning ? RUNNING_SETTINGS : SETTINGS;
      default:
        throw new IllegalArgumentException("未知页面");
    }
  }

  public static boolean opensMenu(int current, int target) {
    return current == target;
  }

  public static Action fromId(int id) {
    for (Action action : Action.values()) if (action.id() == id) return action;
    return null;
  }
}
