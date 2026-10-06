package com.marketdesk;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import android.graphics.Color;
import org.json.JSONObject;
import java.util.*;
import android.os.Bundle;
import android.os.Build;
import android.util.SizeF;
import android.content.res.Configuration;
public class MarketWidget extends AppWidgetProvider {
 public static final class DisplaySnapshot {
  public final Map<String,?> values;
  public final long time;
  private DisplaySnapshot(Context c){values=Collections.unmodifiableMap(new HashMap<>(c.getSharedPreferences("market",0).getAll()));time=System.currentTimeMillis();}
 }
 public static DisplaySnapshot snapshot(Context c){return new DisplaySnapshot(c);}
 public void onUpdate(Context c,AppWidgetManager m,int[] ids){RefreshWorker.schedule(c);render(c);RefreshWorker.now(c);}
 public void onReceive(Context c,Intent i){super.onReceive(c,i);if("com.marketdesk.REFRESH".equals(i.getAction())){RefreshWorker.now(c);android.widget.Toast.makeText(c,"已请求刷新，App与小组件将同步更新",android.widget.Toast.LENGTH_SHORT).show();}}
 public void onDisabled(Context c){androidx.work.WorkManager.getInstance(c).cancelUniqueWork("quotes-periodic");}
 public static void render(Context c){
  render(c,snapshot(c));
 }
 public static void render(Context c,DisplaySnapshot batch){
  AppWidgetManager m=AppWidgetManager.getInstance(c);
  for(int id:m.getAppWidgetIds(new ComponentName(c,MarketWidget.class))){
   Bundle options=m.getAppWidgetOptions(id);
   if(Build.VERSION.SDK_INT>=31){ArrayList<SizeF> sizes=options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
    if(sizes!=null&&!sizes.isEmpty()){Map<SizeF,RemoteViews> layouts=new LinkedHashMap<>();for(SizeF size:sizes){if(size.getWidth()>0&&size.getHeight()>0)layouts.put(size,views(c,Math.round(size.getHeight()),batch));if(layouts.size()==16)break;}if(!layouts.isEmpty()){m.updateAppWidget(id,new RemoteViews(layouts));continue;}}
   }
   m.updateAppWidget(id,views(c,dimensions(c,options)[1],batch));
  }
 }
 public static int[] dimensions(Context c,Bundle options){
  boolean landscape=c.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
  if(Build.VERSION.SDK_INT>=31){ArrayList<SizeF> sizes=options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);SizeF chosen=null;
   if(sizes!=null)for(SizeF size:sizes){if(size.getWidth()<=0||size.getHeight()<=0)continue;if(chosen==null||(landscape?size.getWidth()>chosen.getWidth():size.getWidth()<chosen.getWidth()))chosen=size;}
   if(chosen!=null)return new int[]{Math.round(chosen.getWidth()),Math.round(chosen.getHeight())};
  }
  int minW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300),minH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,280);
  return landscape?new int[]{options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,minW),minH}:new int[]{minW,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,minH)};
 }
 public static RemoteViews views(Context c,int height,DisplaySnapshot batch){
  Map<String,?> saved=batch.values;
  RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);v.removeAllViews(R.id.rows);
  PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  for(int target:new int[]{R.id.widget_root,R.id.title,R.id.rows,R.id.status})v.setOnClickPendingIntent(target,open);
  for(WidgetPresentation.Row item:WidgetPresentation.rows(saved,height,batch.time)){
   RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.quote_row);
   row.setTextViewText(R.id.name,item.name);row.setTextViewText(R.id.meta,item.meta);
   row.setTextViewText(R.id.price,item.price);row.setTextViewText(R.id.change,item.change);row.setTextColor(R.id.change,item.color);
   for(int target:new int[]{R.id.quote_line,R.id.name,R.id.meta,R.id.price,R.id.change})row.setOnClickPendingIntent(target,open);
   row.setContentDescription(R.id.name,item.name+"，"+item.code+"，"+item.meta);v.addView(R.id.rows,row);
  }
  Object attempt=saved.get("attempt");
  v.setTextViewText(R.id.status,(attempt instanceof Long?"更新 "+Quotes.beijing((Long)attempt)+"\n":"")+"可能延迟 · 北京时间");
  v.setOnClickPendingIntent(R.id.refresh,PendingIntent.getBroadcast(c,1,new Intent(c,MarketWidget.class).setAction("com.marketdesk.REFRESH"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
  return v;
 }
 public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle b){render(c);}
}
