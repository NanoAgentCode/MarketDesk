package com.marketdesk.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupMenu;
import java.util.function.Consumer;

/** A native, scrollable popup anchored to the current bottom navigation button. */
public final class PageActionMenu {
  private final Context context;
  private final Consumer<PageActions.Action> execute;
  private PopupMenu active;

  public PageActionMenu(Context context, Consumer<PageActions.Action> execute) {
    this.context = context;
    this.execute = execute;
  }

  public void show(View anchor, int page, int interval, boolean liveRunning) {
    hide();
    PopupMenu popup = new PopupMenu(context, anchor, Gravity.END);
    active = popup;
    int order = 0;
    for (PageActions.Action action : PageActions.forPage(page, liveRunning)) {
      popup.getMenu().add(action.group, action.id(), order++, action.title(interval, page));
    }
    popup.setOnMenuItemClickListener(
        item -> {
          PageActions.Action action = PageActions.fromId(item.getItemId());
          if (action == null) return false;
          execute.accept(action);
          return true;
        });
    popup.setOnDismissListener(
        menu -> {
          if (active == popup) active = null;
        });
    popup.show();
  }

  public void hide() {
    if (active != null) {
      active.dismiss();
      active = null;
    }
  }
}
