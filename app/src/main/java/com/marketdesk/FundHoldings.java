package com.marketdesk;

import org.json.*;
import java.io.IOException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Public holdings and price history, retaining market dates instead of request timestamps. */
public final class FundHoldings {
 static final long DAY=86400000L;
 static LocalDate today(long now){return Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();}
 public static JSONObject parse(String raw,String code,long now) throws Exception {
  if(!code.matches("[0-9]{6}"))throw new IOException("基金代码无效");
  JSONObject root=new JSONObject(raw);
  if(!root.optBoolean("Success",false))throw new IOException("持仓接口返回失败");
  String report=root.getString("Expansion");LocalDate reportDate=LocalDate.parse(report);
  if(reportDate.isAfter(today(now)))throw new IOException("持仓报告日期异常");
  JSONArray input=root.getJSONObject("Datas").optJSONArray("fundStocks"),stocks=new JSONArray();
  Set<String> seen=new HashSet<>();double total=0;
  if(input!=null)for(int i=0;i<input.length();i++){
   JSONObject row=input.getJSONObject(i);double weight=row.getDouble("JZBL");
   if(!Double.isFinite(weight)||weight<0||weight>100)throw new IOException("持仓权重无效");if(weight==0)continue;
   String market=row.optString("NEWTEXCH",""),symbol=row.getString("GPDM").trim();
   if(market.equals("116")&&symbol.matches("[0-9]{1,5}"))symbol=String.format(Locale.ROOT,"%05d",Integer.parseInt(symbol));
   String secid=market+"."+symbol;if(!seen.add(secid))throw new IOException("持仓重复");
   JSONObject stock=new JSONObject().put("code",symbol).put("name",row.optString("GPJC",symbol)).put("weight",weight).put("secid",secid);
   if((market.equals("0")||market.equals("1"))&&symbol.matches("[0-9]{6}"))stock.put("currency","CNY").put("yahoo",symbol+(market.equals("1")?".SS":".SZ"));
  else if(market.equals("116")&&symbol.matches("[0-9]{5}"))stock.put("currency","HKD").put("yahoo",String.format(Locale.ROOT,"%04d",Integer.parseInt(symbol))+".HK");
   else if(Arrays.asList("105","106","107").contains(market)&&symbol.matches("[A-Za-z][A-Za-z0-9.-]{0,14}"))stock.put("currency","USD").put("yahoo",symbol.replace('.','-'));
   else if(market.isEmpty()||market.equals("--")){
    // These issuer codes were verified; an unknown six-digit code is never assumed to be Korean.
    String name=stock.getString("name"),yahoo=null,currency=null;
    if(symbol.equals("000660")&&(name.equalsIgnoreCase("SK海力士")||name.equalsIgnoreCase("SK HYNIX"))){yahoo="000660.KS";currency="KRW";}
    else if(symbol.equals("005930")&&(name.equals("三星电子")||name.equalsIgnoreCase("SAMSUNG ELECTRONICS"))){yahoo="005930.KS";currency="KRW";}
    else if(symbol.equalsIgnoreCase("285A")&&(name.equalsIgnoreCase("KIOXIA")||name.equals("铠侠"))){yahoo="285A.T";currency="JPY";}
    if(yahoo!=null)stock.put("currency",currency).put("yahoo",yahoo).put("secid","Y:"+yahoo);
   }
   stocks.put(stock);total+=weight;
  }
  if(total>100.01)throw new IOException("披露权重超过基金净值");
  return new JSONObject().put("code",code).put("reportDate",report).put("disclosedWeight",total).put("stocks",stocks).put("checkedAt",now);
 }
 static ZoneId zone(String secid){return ZoneId.of(secid.startsWith("105.")||secid.startsWith("106.")||secid.startsWith("107.")?"America/New_York":"Asia/Shanghai");}
 static String fxSymbol(String currency){switch(currency){case "USD":return "CNY=X";case "HKD":return "HKDCNY=X";case "KRW":return "KRWCNY=X";case "JPY":return "JPYCNY=X";default:return null;}}
 static void validatePeriod(LocalDate base,LocalDate target,String navDate,long now) throws Exception {
  LocalDate nav=LocalDate.parse(navDate);
  if(base==null||target==null||base.isAfter(nav)||ChronoUnit.DAYS.between(base,nav)>7)throw new IOException("缺少净值日期对应的历史价格");
  if(target.isBefore(nav)||target.isAfter(today(now))||ChronoUnit.DAYS.between(target,today(now))>14)throw new IOException("行情日期过旧或异常");
 }
 public static JSONObject eastmoneyHistory(String raw,String secid,String navDate,long now) throws Exception {
  JSONObject root=new JSONObject(raw);if(root.optInt("rc",0)!=0||root.isNull("data"))throw new IOException("历史行情不可用");
  JSONObject data=root.getJSONObject("data");int dot=secid.indexOf('.');
  if(!data.getString("code").equalsIgnoreCase(secid.substring(dot+1))||data.getInt("market")!=Integer.parseInt(secid.substring(0,dot)))throw new IOException("历史行情代码或市场不匹配");
  JSONArray rows=data.getJSONArray("klines");LocalDate nav=LocalDate.parse(navDate),base=null,last=null;double basePrice=0,lastPrice=0;
  for(int i=0;i<rows.length();i++){
   String[] fields=rows.getString(i).split(",",-1);if(fields.length<3)continue;
   LocalDate date=LocalDate.parse(fields[0]);double close=Double.parseDouble(fields[2]);if(!FundValuation.positive(close))continue;
   if(!date.isAfter(nav)&&(base==null||date.isAfter(base))){base=date;basePrice=close;}
   if(!date.isAfter(today(now))&&(last==null||date.isAfter(last))){last=date;lastPrice=close;}
  }
  validatePeriod(base,last,navDate,now);
  return new JSONObject().put("ratio",lastPrice/basePrice).put("basePrice",basePrice).put("latestPrice",lastPrice).put("priceFactor",1).put("navDate",navDate).put("baseDate",base.toString()).put("date",last.toString()).put("time",last.atStartOfDay(zone(secid)).toInstant().toEpochMilli()).put("kind","daily").put("source","东方财富 · 前复权日线参考");
 }
 public static JSONObject yahooHistory(String raw,String symbol,String navDate,String currency,long now) throws Exception {
  JSONObject chart=new JSONObject(raw).getJSONObject("chart");if(!chart.isNull("error"))throw new IOException("历史行情返回失败");
  JSONObject result=chart.getJSONArray("result").getJSONObject(0),meta=result.getJSONObject("meta");
  if(!symbol.equalsIgnoreCase(meta.getString("symbol"))||!currency.equals(meta.getString("currency")))throw new IOException("历史行情代码或币种不匹配");
  ZoneId zone=ZoneId.of(meta.getString("exchangeTimezoneName"));JSONArray times=result.getJSONArray("timestamp"),closes=result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close");
  JSONArray adjRows=result.getJSONObject("indicators").optJSONArray("adjclose"),adjusted=adjRows!=null&&adjRows.length()>0?adjRows.getJSONObject(0).optJSONArray("adjclose"):null;
  LocalDate nav=LocalDate.parse(navDate),base=null,last=null;double basePrice=0,lastPrice=0,lastRaw=0;long lastTime=0;
  for(int i=0;i<times.length()&&i<closes.length();i++){
   long time=times.optLong(i,0)*1000;double close=closes.optDouble(i,Double.NaN),price=adjusted==null?close:adjusted.optDouble(i,Double.NaN);
   if(time<=0||time>now+300000||!FundValuation.positive(close)||!FundValuation.positive(price))continue;
   LocalDate date=Instant.ofEpochMilli(time).atZone(zone).toLocalDate();
   if(!date.isAfter(nav)&&(base==null||date.isAfter(base))){base=date;basePrice=price;}
   if(time>=lastTime){last=date;lastPrice=price;lastRaw=close;lastTime=time;}
  }
  double factor=lastRaw>0?lastPrice/lastRaw:1;
  double live=meta.optDouble("regularMarketPrice",Double.NaN);long liveTime=meta.optLong("regularMarketTime")*1000;boolean liveUsed=false;
  if(lastRaw>0&&FundValuation.positive(live)&&liveTime>=lastTime&&liveTime<=now+300000){lastPrice=live*lastPrice/lastRaw;lastTime=liveTime;last=Instant.ofEpochMilli(liveTime).atZone(zone).toLocalDate();liveUsed=true;}
  validatePeriod(base,last,navDate,now);
  return new JSONObject().put("ratio",lastPrice/basePrice).put("basePrice",basePrice).put("latestPrice",lastPrice).put("priceFactor",factor).put("navDate",navDate).put("baseDate",base.toString()).put("date",last.toString()).put("time",lastTime).put("kind",liveUsed?"quote":"daily").put("source","Yahoo · 历史及最新参考");
 }
 public static Map<String,JSONObject> latestBatch(String raw,long now) throws Exception {
  JSONObject root=new JSONObject(raw);if(root.optInt("rc",0)!=0||root.isNull("data"))throw new IOException("持仓行情暂不可用");
  JSONArray rows=root.getJSONObject("data").getJSONArray("diff");Map<String,JSONObject> out=new HashMap<>();
  for(int i=0;i<rows.length();i++){
   JSONObject row=rows.getJSONObject(i);double price=row.optDouble("f2",Double.NaN);long time=row.optLong("f124")*1000;
   if(!FundValuation.positive(price)||time<=0||time>now+300000||now-time>15*DAY)continue;
   String secid=row.getInt("f13")+"."+row.getString("f12");
   out.put(secid,new JSONObject().put("price",price).put("time",time));
  }
  return out;
 }
 public static JSONObject withLatest(JSONObject history,JSONObject latest,String secid,long now) throws Exception {
  JSONObject out=new JSONObject(history.toString());if(latest==null||out.has("error"))return out;
  long time=latest.getLong("time");LocalDate date=Instant.ofEpochMilli(time).atZone(zone(secid)).toLocalDate();
  if(time<out.getLong("time")||date.isBefore(LocalDate.parse(out.getString("date"))))return out;
  validatePeriod(LocalDate.parse(out.getString("baseDate")),date,out.getString("navDate"),now);
  double price=latest.getDouble("price")*out.optDouble("priceFactor",1);
  if(!FundValuation.positive(price))return out;
  return out.put("ratio",price/out.getDouble("basePrice")).put("latestPrice",price).put("time",time).put("date",date.toString()).put("kind","quote").put("source",out.optString("source")+"＋东方财富延迟行情");
 }
}
