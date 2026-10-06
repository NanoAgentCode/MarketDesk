package com.marketdesk;

import org.json.JSONObject;
import java.util.*;

/** Shared, immutable display rows built from a single preference snapshot. */
public final class WidgetPresentation {
 public static final class Row {
  public final String name,code,meta,price,change;
  public final int color;
  Row(String name,String code,String meta,String price,String change,int color){this.name=name;this.code=code;this.meta=meta;this.price=price;this.change=change;this.color=color;}
 }
 // 24dp outer padding + 24dp title + one 40dp combined footer.
 public static int capacity(int height){return Math.max(1,Math.min(20,(height-88)/42));}
 public static List<Row> rows(Map<String,?> saved,int height,long now){
  List<Row> out=new ArrayList<>();String watch=saved.get("watch") instanceof String?(String)saved.get("watch"):Quotes.DEFAULTS;
  for(String line:watch.split("\\n")){
   String[] item=line.trim().split("\\|",2);if(item.length!=2)continue;
   if(out.size()>=capacity(height))break;
   JSONObject q;try{q=new JSONObject(String.valueOf(saved.get(item[1])));}catch(Exception e){q=new JSONObject();}
   boolean available=q.has("price"), failed=saved.containsKey("error:"+item[1]);
   boolean stale=now-q.optLong("received")>20*60*1000L;
   long dataTime=q.optLong("dataTime");boolean old=dataTime>0&&now-dataTime>24*60*60*1000L;
   String meta=available?q.optString("time")+(old?" 历史":"")+(q.optString("kind").equals("minute")?" 分时":"")+(failed||stale?" 缓存":"")+(item[1].startsWith("F:")?" 估算":""):"暂不可用";
   double change=q.optDouble("change");int color=change>0?0xffff6771:change<0?0xff41d399:0xffcccccc;
   out.add(new Row(item[0],item[1],meta,available?String.format(Locale.CHINA,"%.3f",q.optDouble("price")):"—",q.has("change")?String.format(Locale.CHINA,"%+.2f%%",change):"—",color));
  }
  return Collections.unmodifiableList(out);
 }
}
