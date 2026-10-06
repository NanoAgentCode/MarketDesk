package com.marketdesk;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import android.graphics.Color;
import org.json.JSONObject;
import java.util.*;
public class MarketWidget extends AppWidgetProvider {
 public static final class DisplaySnapshot {
  public final Map<String,?> values;
  public final long time;
  private DisplaySnapshot(Context c){values=Collections.unmodifiableMap(new HashMap<>(c.getSharedPreferences("market",0).getAll()));time=System.currentTimeMillis();}
 }
 public static DisplaySnapshot snapshot(Context c){return new DisplaySnapshot(c);}
 public void onUpdate(Context c,AppWidgetManager m,int[] ids){RefreshWorker.schedule(c);render(c);RefreshWorker.now(c);}
 public void onReceive(Context c,Intent i){super.onReceive(c,i);if("com.marketdesk.REFRESH".equals(i.getAction())) RefreshWorker.now(c);}
 public void onDisabled(Context c){androidx.work.WorkManager.getInstance(c).cancelUniqueWork("quotes-periodic");}
 public static void render(Context c){
  render(c,snapshot(c));
 }
 public static void render(Context c,DisplaySnapshot batch){
  AppWidgetManager m=AppWidgetManager.getInstance(c);
  for(int id:m.getAppWidgetIds(new ComponentName(c,MarketWidget.class))){
   int height=m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,280);
   m.updateAppWidget(id,views(c,height,batch));
  }
 }
 public static RemoteViews views(Context c,int height,DisplaySnapshot batch){
  Map<String,?> saved=batch.values;
  RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);v.removeAllViews(R.id.rows);
  for(WidgetPresentation.Row item:WidgetPresentation.rows(saved,height,batch.time)){
   RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.quote_row);
   row.setTextViewText(R.id.name,item.name);row.setTextViewText(R.id.meta,item.meta);
   row.setTextViewText(R.id.price,item.price);row.setTextViewText(R.id.change,item.change);row.setTextColor(R.id.change,item.color);
   row.setContentDescription(R.id.name,item.name+"，"+item.code+"，"+item.meta);v.addView(R.id.rows,row);
  }
  Object attempt=saved.get("attempt");
  v.setTextViewText(R.id.status,(attempt instanceof Long?"更新尝试 "+Quotes.beijing((Long)attempt)+" · ":"")+"行情可能延迟 · 北京时间");
  v.setOnClickPendingIntent(R.id.title,PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
  v.setOnClickPendingIntent(R.id.refresh,PendingIntent.getBroadcast(c,1,new Intent(c,MarketWidget.class).setAction("com.marketdesk.REFRESH"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
  return v;
 }
 public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle b){render(c);}
}
