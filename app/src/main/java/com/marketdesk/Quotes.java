package com.marketdesk;

import android.content.Context;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class Quotes {
 static String beijing(long millis) {java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA);f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));return f.format(new Date(millis));}
 public static final String DEFAULTS = "纳斯达克指数|Y:^IXIC\n纳斯达克100|Y:^NDX\n恒生科技指数|Y:HSTECH.HK\n港股通创新药指数|E:2.931250\n电网设备ETF 华夏|E:0.159326";
 public static String watch(Context c) { return c.getSharedPreferences("market",0).getString("watch", DEFAULTS); }
 public static List<String[]> items(Context c) {
  List<String[]> out = new ArrayList<>();
  for(String line:watch(c).split("\\n")) { String[] p=line.trim().split("\\|",2); if(p.length==2) out.add(p); }
  return out;
 }
 public static void validate(String text) {
  if(text.trim().isEmpty()) throw new IllegalArgumentException("至少保留一个自选");
  if(text.trim().split("\\n").length>20) throw new IllegalArgumentException("最多20个自选");
  for(String line:text.trim().split("\\n")) {
   String[] p=line.trim().split("\\|",2);
   if(p.length!=2 || p[0].trim().isEmpty() || !p[1].matches("(Y:[A-Za-z0-9^.=_-]+|E:[0-9]+\\.[A-Za-z0-9]+|F:[0-9]{6})")) throw new IllegalArgumentException("格式错误："+line);
  }
 }
 static String get(String url) throws Exception {
  HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
  conn.setConnectTimeout(6000); conn.setReadTimeout(6000); conn.setRequestProperty("User-Agent","Mozilla/5.0");
  if(conn.getURL().getHost().endsWith(".eastmoney.com"))conn.setRequestProperty("Referer","https://quote.eastmoney.com/");
  try { if(conn.getResponseCode()!=200) throw new IOException("HTTP "+conn.getResponseCode());
   try(InputStream in=conn.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] b=new byte[4096]; int n; while((n=in.read(b))!=-1) out.write(b,0,n); return out.toString("UTF-8"); }
  } finally {conn.disconnect();}
 }
 static JSONObject eastmoney(String secid) throws Exception {
  try {
   JSONObject result=EastmoneyParser.latest(get("https://push2.eastmoney.com/api/qt/stock/get?secid="+secid+"&fltt=2&invt=2&fields=f43,f60,f170,f59,f86"),secid);
   return finishEastmoney(result);
  }catch(Exception latestError){
   try {
    JSONObject result=EastmoneyParser.trends(get("https://push2his.eastmoney.com/api/qt/stock/trends2/get?secid="+secid+"&ndays=1&iscr=0&fields1=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11&fields2=f51,f52,f53,f54,f55,f56,f57,f58"),secid);
    return finishEastmoney(result);
   }catch(Exception trendsError){
    try {
     JSONObject result=EastmoneyParser.daily(get("https://push2his.eastmoney.com/api/qt/stock/kline/get?secid="+secid+"&klt=101&fqt=0&lmt=2&end=20500101&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"),secid);
     return finishEastmoney(result);
    }catch(Exception dailyError){
     String symbol=QuoteCode.yahooFallback(secid);
     if(!symbol.isEmpty())try{return fetch("Y:"+symbol).put("source","Yahoo · 同代码备用行情（可能延迟）").put("kind","fallback");}catch(Exception backupError){dailyError.addSuppressed(backupError);}
     dailyError.addSuppressed(latestError);dailyError.addSuppressed(trendsError);throw dailyError;
    }
   }
  }
 }
 static JSONObject finishEastmoney(JSONObject result) throws Exception {
  long time=result.getLong("dataTime");
  String display=result.optString("kind").equals("daily")?Instant.ofEpochMilli(time).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()+" 日线":beijing(time);
  return result.put("time",display).put("received",System.currentTimeMillis());
 }
 public static String errorText(Exception e) {
  if(e instanceof java.net.SocketTimeoutException)return "网络超时，请稍后刷新";
  if(e instanceof java.net.UnknownHostException)return "无法连接行情源，请检查网络";
  if(e instanceof javax.net.ssl.SSLException)return "行情源连接中断，请稍后刷新";
  if(e instanceof org.json.JSONException||e instanceof IllegalArgumentException)return "行情源数据缺失或格式变化";
  if(e.getMessage()!=null&&e.getMessage().startsWith("HTTP "))return "行情源返回 "+e.getMessage();
  return "行情获取失败，请稍后刷新";
 }
 public static boolean historical(JSONObject q){long time=q.optLong("dataTime",0);return time>0&&System.currentTimeMillis()-time>24*60*60*1000L;}
 static JSONObject fetch(String code) throws Exception {
  JSONObject q=new JSONObject(); double price, change; String time, unit, source;
  if(code.startsWith("Y:")) {
   JSONObject m=new JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/"+URLEncoder.encode(code.substring(2),"UTF-8")+"?interval=1d&range=5d")).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta");
   if(!m.getString("symbol").equalsIgnoreCase(code.substring(2)))throw new IOException("返回代码不匹配");
   price=m.getDouble("regularMarketPrice"); double prev=m.getDouble("chartPreviousClose");
   // previousClose is the previous trading session; chartPreviousClose may be the range baseline.
   if(m.has("previousClose")) prev=m.getDouble("previousClose");
   else {
    JSONObject r=new JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/"+URLEncoder.encode(code.substring(2),"UTF-8")+"?interval=1d&range=1d")).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta");
    prev=r.getDouble("chartPreviousClose");
   }
   if(prev<=0) throw new IOException("昨收无效"); change=(price/prev-1)*100;
   q.put("dataTime",m.getLong("regularMarketTime")*1000);time=beijing(m.getLong("regularMarketTime")*1000); unit=m.optString("currency",""); source="Yahoo · 可能延迟";
  } else if(code.startsWith("E:")) {
   return eastmoney(code.substring(2));
  } else {
   String raw=get("https://fundgz.1234567.com.cn/js/"+code.substring(2)+".js");
   JSONObject d=new JSONObject(raw.substring(raw.indexOf('(')+1,raw.lastIndexOf(')')));
   price=d.getDouble("gsz"); change=d.getDouble("gszzl"); time=d.getString("gztime"); unit="估算净值"; source="天天基金 · 估算非成交价";
  }
  if(!Double.isFinite(price)||!Double.isFinite(change)) throw new IOException("无效数据");
  return q.put("price",price).put("change",change).put("time",time).put("unit",unit).put("source",source).put("received",System.currentTimeMillis());
 }
 public static synchronized void refresh(Context c) {
  android.content.SharedPreferences prefs=c.getSharedPreferences("market",0);
  ExecutorService pool=Executors.newFixedThreadPool(4);
  List<Future<?>> tasks=new ArrayList<>();
  Map<String,String> results=new ConcurrentHashMap<>(),errors=new ConcurrentHashMap<>();
  for(String[] item:items(c)) tasks.add(pool.submit(()->{
   try { JSONObject q=fetch(item[1]); results.put(item[1],q.toString()); }
   catch(Exception e) {errors.put(item[1],errorText(e));android.util.Log.w("MarketDesk","Quote refresh failed: "+item[1],e);}
  }));
  for(Future<?> task:tasks) try {task.get();} catch(Exception ignored) {}
  pool.shutdown();android.content.SharedPreferences.Editor update=prefs.edit();
  for(Map.Entry<String,String> entry:results.entrySet())update.putString(entry.getKey(),entry.getValue()).remove("error:"+entry.getKey());
  for(Map.Entry<String,String> entry:errors.entrySet())update.putString("error:"+entry.getKey(),entry.getValue());
  update.putLong("attempt",System.currentTimeMillis()).apply();MarketWidget.render(c);
 }
 public static JSONObject cached(Context c,String code) { try {return new JSONObject(c.getSharedPreferences("market",0).getString(code,"{}"));}catch(Exception e){return new JSONObject();} }
 public static String line(Context c,String code) {
  JSONObject q=cached(c,code); if(!q.has("price")) return "暂无行情 · "+code+"\n"+c.getSharedPreferences("market",0).getString("error:"+code,"等待刷新");
  boolean failed=c.getSharedPreferences("market",0).contains("error:"+code);
  boolean stale=System.currentTimeMillis()-q.optLong("received")>20*60*1000;
  return String.format(Locale.CHINA,"%.3f  %+.2f%%\n%s %s · %s%s%s",q.optDouble("price"),q.optDouble("change"),q.optString("unit"),q.optString("time"),q.optString("source"),historical(q)?" · 历史行情":"",failed?" · "+c.getSharedPreferences("market",0).getString("error:"+code,"")+"，缓存":stale?" · 缓存待更新":"");
 }
}
