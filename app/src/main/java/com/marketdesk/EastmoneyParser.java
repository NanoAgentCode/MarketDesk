package com.marketdesk;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Parse each endpoint's own numeric format and retain its actual market timestamp. */
public final class EastmoneyParser {
 private static final ZoneId BEIJING=ZoneId.of("Asia/Shanghai");
 private static final DateTimeFormatter MINUTE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
 private static JSONObject data(String raw,String secid) throws Exception {
  JSONObject root=new JSONObject(raw);
  if(root.optInt("rc",0)!=0 || root.isNull("data"))throw new IOException("接口未返回行情");
  JSONObject d=root.getJSONObject("data");
  // Chart endpoints carry the code and market. Never substitute a similar ETF or index.
  if(d.has("code") && !d.getString("code").equals(secid.substring(secid.indexOf('.')+1)))throw new IOException("返回代码不匹配");
  if(d.has("market") && d.getInt("market")!=Integer.parseInt(secid.substring(0,secid.indexOf('.'))))throw new IOException("返回市场不匹配");
  return d;
 }
 private static JSONObject quote(double price,double change,long timestamp,String kind,String source) throws Exception {
  if(!Double.isFinite(price)||price<=0||!Double.isFinite(change)||timestamp<=0)throw new IOException("行情数值或时间无效");
  return new JSONObject().put("price",price).put("change",change).put("dataTime",timestamp).put("kind",kind).put("source",source).put("unit","");
 }
 public static JSONObject latest(String raw,String secid) throws Exception {
  JSONObject d=data(raw,secid);
  // This request explicitly uses fltt=2: values are decimal prices and percent points.
  double price=d.getDouble("f43"), previous=d.getDouble("f60");
  if(previous<=0)throw new IOException("昨收无效");
  double change=d.isNull("f170")||"-".equals(d.optString("f170"))?(price/previous-1)*100:d.getDouble("f170");
  return quote(price,change,d.getLong("f86")*1000,"latest","东方财富 · 可能延迟");
 }
 public static JSONObject trends(String raw,String secid) throws Exception {
  JSONObject d=data(raw,secid);JSONArray rows=d.getJSONArray("trends");
  if(rows.length()==0)throw new IOException("暂无分时数据");
  String[] values=rows.getString(rows.length()-1).split(",",-1);
  if(values.length<3)throw new IOException("分时数据格式异常");
  double price=Double.parseDouble(values[2]), previous=d.getDouble("preClose");
  if(previous<=0)throw new IOException("昨收无效");
  long time=LocalDateTime.parse(values[0],MINUTE).atZone(BEIJING).toInstant().toEpochMilli();
  return quote(price,(price/previous-1)*100,time,"minute","东方财富 · 备用分时（可能延迟）");
 }
 public static JSONObject daily(String raw,String secid) throws Exception {
  JSONObject d=data(raw,secid);JSONArray rows=d.getJSONArray("klines");
  if(rows.length()==0)throw new IOException("暂无日线数据");
  String[] values=rows.getString(rows.length()-1).split(",",-1);
  if(values.length<9)throw new IOException("日线数据格式异常");
  // f59 in the requested daily fields is daily percentage, not a 1-minute change.
  double price=Double.parseDouble(values[2]),change=Double.parseDouble(values[8]);
  long time=LocalDate.parse(values[0]).atStartOfDay(BEIJING).toInstant().toEpochMilli();
  return quote(price,change,time,"daily","东方财富 · 备用日线（非实时）");
 }
}
