package com.marketdesk.ui;

import static com.marketdesk.ui.PageActions.Action.*;
import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class PageActionsTest {
  @Test
  public void watchlistMenuKeepsRefreshAndEditor() {
    assertEquals(Arrays.asList(REFRESH, EDIT_WATCHLIST), PageActions.forPage(0));
  }

  @Test
  public void widgetMenuKeepsEveryPageAction() {
    assertEquals(
        Arrays.asList(EDIT_WATCHLIST, CHOOSE_PREVIEW, PIN_WIDGET, WIDGET_HELP),
        PageActions.forPage(1));
  }

  @Test
  public void settingsMenuKeepsLiveFundAndSystemActions() {
    assertEquals(
        Arrays.asList(
            EDIT_WATCHLIST,
            ADD_PRESET,
            REFRESH_INTERVAL,
            START_LIVE,
            SYSTEM_SETTINGS,
            MANAGE_FUNDS),
        PageActions.forPage(2));
  }

  @Test
  public void runningLiveServiceReplacesStartWithStopWithoutAddingAnItem() {
    List<PageActions.Action> stopped = PageActions.forPage(2, false),
        running = PageActions.forPage(2, true);
    assertEquals(6, stopped.size());
    assertEquals(6, running.size());
    assertTrue(stopped.contains(START_LIVE));
    assertFalse(stopped.contains(STOP_LIVE));
    assertTrue(running.contains(STOP_LIVE));
    assertFalse(running.contains(START_LIVE));
  }

  @Test
  public void batchFundImportIsNotAnAvailableAction() {
    assertTrue(PageActions.forPage(2).contains(MANAGE_FUNDS));
    for (PageActions.Action action : PageActions.Action.values()) {
      assertFalse(action.title(30).contains("指定"));
      assertFalse(action.title(30).contains("5只"));
    }
  }

  @Test
  public void mergedEditorClearlyIncludesFundsAndEtfsInSettings() {
    assertEquals("编辑自选 / 基金 / ETF", EDIT_WATCHLIST.title(30, 2));
    assertEquals("编辑自选列表", EDIT_WATCHLIST.title(30, 0));
  }

  @Test
  public void menuListsCannotBeChangedByThePresenter() {
    try {
      PageActions.forPage(0).clear();
      fail("expected immutable menu");
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void firstTapNavigatesAndRepeatedTapOpensMenu() {
    assertFalse(PageActions.opensMenu(0, 1));
    assertTrue(PageActions.opensMenu(1, 1));
    assertTrue(PageActions.opensMenu(0, 0));
    assertTrue(PageActions.opensMenu(2, 2));
  }

  @Test
  public void menuIdsUniquelyMapBackToTheAction() {
    Set<Integer> ids = new HashSet<>();
    for (PageActions.Action action : PageActions.Action.values()) {
      assertTrue(ids.add(action.id()));
      assertSame(action, PageActions.fromId(action.id()));
    }
    assertNull(PageActions.fromId(0));
    assertNull(PageActions.fromId(999));
  }

  @Test
  public void intervalLabelReflectsCurrentPreference() {
    assertEquals("刷新间隔：60 秒", REFRESH_INTERVAL.title(60));
    assertEquals("刷新", REFRESH.title(60));
  }
}
