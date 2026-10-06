package com.marketdesk;
import android.content.*;
public class WidgetPinnedReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){if(!"com.marketdesk.PINNED".equals(i.getAction()))return;long token=i.getLongExtra("request_token",0);if(token==0)return;
  c.getSharedPreferences("market",0).edit().putLong("pin_success",token).apply();RefreshWorker.schedule(c);MarketWidget.render(c);RefreshWorker.now(c);
 }
}
