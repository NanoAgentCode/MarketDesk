package com.marketdesk;

import org.json.*;
import java.net.URLEncoder;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Shared bounded requests: holdings daily, price/FX results (including failures) for 60 seconds. */
public final class FundReferenceData {
 interface Fetch {String get(String url) throws Exception;}
 private static final class Entry {
  final FutureTask<JSONObject> task;long completedAt;
  Entry(FutureTask<JSONObject> task){this.task=task;}
 }
 private final Fetch fetch;private final LongSupplier clock;private final ExecutorService pool;
 private final Map<String,Entry> cache=new LinkedHashMap<>();
 public FundReferenceData(){this(Quotes::get,System::currentTimeMillis,Executors.newFixedThreadPool(4));}
 FundReferenceData(Fetch fetch,LongSupplier clock,ExecutorService pool){this.fetch=fetch;this.clock=clock;this.pool=pool;}
 private synchronized FutureTask<JSONObject> request(String key,long ttl,Callable<JSONObject> load){
  long now=clock.getAsLong();Entry existing=cache.get(key);
  if(existing!=null){
   if(!existing.task.isDone())return existing.task;
   try{if(existing.task.get().has("error"))ttl=Math.min(ttl,60000);}catch(Exception ignored){ttl=Math.min(ttl,60000);}
   if(now-existing.completedAt<ttl)return existing.task;
  }
  FutureTask<JSONObject> task=new FutureTask<>(()->{
   try{return load.call();}catch(Exception e){return new JSONObject().put("error",e.getMessage()==null?"取数失败":e.getMessage());}
   finally{synchronized(FundReferenceData.this){Entry entry=cache.get(key);if(entry!=null)entry.completedAt=clock.getAsLong();}}
  });
  cache.put(key,new Entry(task));
  // Bound old entries without interrupting requests still shared by another fund.
  if(cache.size()>512){Iterator<Map.Entry<String,Entry>> it=cache.entrySet().iterator();while(it.hasNext()&&cache.size()>512)if(it.next().getValue().task.isDone())it.remove();}
  pool.execute(task);return task;
 }
 private JSONObject await(FutureTask<JSONObject> task,long deadline) throws Exception {
  long wait=deadline-System.nanoTime();if(wait<=0)throw new TimeoutException("持仓参考查询超时");return new JSONObject(task.get(wait,TimeUnit.NANOSECONDS).toString());
 }
 public JSONObject reference(String code,JSONObject nav){
  long now=clock.getAsLong(),deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);
  try{
   JSONObject holdings=await(request("holdings:"+code,FundHoldings.DAY,()->FundHoldings.parse(fetch.get("https://fundmobapi.eastmoney.com/FundMNewApi/FundMNInverstPosition?FCODE="+code+"&deviceid=marketdesk&plat=Android&product=EFund&version=6.2.8"),code,clock.getAsLong())),deadline);
   if(holdings.has("error"))return new JSONObject().put("reason","持仓暂不可用："+holdings.optString("error")).put("checkedAt",now);
   String date=nav==null?"":nav.optString("date");
   if(nav==null||date.isEmpty()||ChronoUnit.DAYS.between(LocalDate.parse(date),FundHoldings.today(now))>45||ChronoUnit.DAYS.between(LocalDate.parse(holdings.getString("reportDate")),FundHoldings.today(now))>190)return FundReference.calculate(nav,holdings,Collections.emptyMap(),now);
   JSONArray stocks=holdings.getJSONArray("stocks");Map<String,FutureTask<JSONObject>> jobs=new LinkedHashMap<>();List<String> secids=new ArrayList<>();
   for(int i=0;i<stocks.length();i++){
    JSONObject stock=stocks.getJSONObject(i);String secid=stock.getString("secid"),currency=stock.optString("currency");if(currency.isEmpty())continue;
    if(!secid.startsWith("Y:"))secids.add(secid);jobs.put(secid,request("stock:"+secid+":"+date,60000,()->stockReturn(stock,date)));
    if(!currency.equals("CNY")&&!jobs.containsKey(currency)){
     String symbol=FundHoldings.fxSymbol(currency);if(symbol==null)continue;
     jobs.put(currency,request("fx:"+symbol+":"+date,60000,()->FundHoldings.yahooHistory(fetch.get(yahooUrl(symbol,date)),symbol,date,"CNY",clock.getAsLong())));
    }
   }
   FutureTask<JSONObject> quoteJob=null;
   if(!secids.isEmpty()){Collections.sort(secids);String list=String.join(",",secids);quoteJob=request("quotes:"+list,60000,()->new JSONObject().put("raw",fetch.get("https://push2delay.eastmoney.com/api/qt/ulist.np/get?secids="+list+"&fltt=2&invt=2&fields=f12,f13,f2,f124")));}
   Map<String,JSONObject> samples=new LinkedHashMap<>();
   for(Map.Entry<String,FutureTask<JSONObject>> entry:jobs.entrySet())try{samples.put(entry.getKey(),await(entry.getValue(),deadline));}catch(Exception e){samples.put(entry.getKey(),new JSONObject().put("error","查询超时或暂不可用"));}
   if(quoteJob!=null)try{
    JSONObject quotes=await(quoteJob,deadline);Map<String,JSONObject> latest=FundHoldings.latestBatch(quotes.getString("raw"),clock.getAsLong());
    for(String secid:secids){JSONObject history=samples.get(secid);if(history!=null&&!history.has("error"))samples.put(secid,FundHoldings.withLatest(history,latest.get(secid),secid,clock.getAsLong()));}
   }catch(Exception ignored){/* Keep dated daily references when the optional latest batch fails. */}
   return FundReference.calculate(nav,holdings,samples,clock.getAsLong());
  }catch(Exception e){return failure("持仓参考暂不可用",now);}
 }
 private static JSONObject failure(String reason,long now){try{return new JSONObject().put("reason",reason).put("checkedAt",now);}catch(JSONException e){return new JSONObject();}}
 private JSONObject stockReturn(JSONObject stock,String date) throws Exception {
  String secid=stock.getString("secid");
  if(secid.startsWith("Y:")){String symbol=stock.getString("yahoo");return FundHoldings.yahooHistory(fetch.get(yahooUrl(symbol,date)),symbol,date,stock.getString("currency"),clock.getAsLong());}
  try{
   String begin=LocalDate.parse(date).minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE),end=FundHoldings.today(clock.getAsLong()).format(DateTimeFormatter.BASIC_ISO_DATE);
   String url="https://push2his.eastmoney.com/api/qt/stock/kline/get?secid="+secid+"&klt=101&fqt=1&beg="+begin+"&end="+end+"&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61";
   return FundHoldings.eastmoneyHistory(fetch.get(url),secid,date,clock.getAsLong());
  }catch(Exception first){
   String symbol=stock.getString("yahoo");return FundHoldings.yahooHistory(fetch.get(yahooUrl(symbol,date)),symbol,date,stock.getString("currency"),clock.getAsLong());
  }
 }
 static String yahooUrl(String symbol,String date) throws java.io.UnsupportedEncodingException {
  long start=LocalDate.parse(date).minusDays(7).atStartOfDay(ZoneId.of("UTC")).toEpochSecond();
  return "https://query1.finance.yahoo.com/v8/finance/chart/"+URLEncoder.encode(symbol,"UTF-8")+"?interval=1d&period1="+start+"&period2="+(System.currentTimeMillis()/1000+86400)+"&events=div%2Csplits";
 }
}
