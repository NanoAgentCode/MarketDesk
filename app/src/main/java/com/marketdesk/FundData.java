package com.marketdesk;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
public final class FundData {
 private static final Map<String,JSONObject> navCache=new ConcurrentHashMap<>(),etfCache=new ConcurrentHashMap<>();
 public static JSONObject valuation(String code) throws Exception {
  long now=System.currentTimeMillis();JSONObject old=navCache.get(code);if(old!=null&&now-old.optLong("checkedAt")<60000)return new JSONObject(old.toString());
  Exception failure=null;for(String host:new String[]{"fundcomapi.tiantianfunds.com","fundcomapi.eastmoney.com"})try{
   JSONObject values=FundValuation.parse(Quotes.get("https://"+host+"/mm/newCore/FundValuationLast?FCODES="+code+"&FIELDS=FCODE,SHORTNAME,GSZZL,GZTIME,GSZ,NAV,PDATE"),code).put("checkedAt",now);navCache.put(code,new JSONObject(values.toString()));return values;
  }catch(Exception e){failure=e;}throw failure;
 }
 public static JSONObject etf(String secid) {
  long now=System.currentTimeMillis();JSONObject old=etfCache.get(secid);if(old!=null&&now-old.optLong("checkedAt")<60000)try{return new JSONObject(old.toString());}catch(Exception ignored){}
  JSONObject values=new JSONObject();ExecutorService pool=Executors.newFixedThreadPool(2);
  Future<JSONObject> nav=pool.submit(()->valuation(secid.substring(2)));
  Future<JSONObject> iopv=pool.submit(()->EtfReference.parse(Quotes.get("https://push2delay.eastmoney.com/api/qt/ulist.np/get?secids="+secid+"&fltt=2&invt=2&fields=f12,f13,f2,f124,f297,f441"),secid));
  try{
   values.put("type","etf").put("code",secid.substring(2)).put("checkedAt",now);
   try{JSONObject result=nav.get();if(result.has("nav"))values.put("nav",result.getJSONObject("nav"));if(result.has("estimate"))values.put("estimate",result.getJSONObject("estimate"));}catch(Exception e){values.put("valuationError","估值/净值暂不可用");}
   try{values.put("iopv",iopv.get());}catch(Exception e){values.put("iopvError","IOPV暂不可用，成交价仍可查看");}
   etfCache.put(secid,new JSONObject(values.toString()));
  }catch(Exception ignored){}finally{pool.shutdownNow();}return values;
 }
 public static String details(JSONObject quote){
  JSONObject values=quote.optJSONObject("fundMetrics");if(values==null)return "";StringBuilder out=new StringBuilder("\n\n基金净值与估值");
  JSONObject iopv=values.optJSONObject("iopv");if(values.optString("type").equals("etf")){
   if(iopv==null)out.append("\nIOPV：暂不可用");else{
    out.append(String.format(Locale.CHINA,"\nIOPV参考净值：%.4f",iopv.optDouble("value")));long time=iopv.optLong("packageTime");out.append("\n行情包时间：").append(time>0?Quotes.beijing(time):"未提供");
    if(iopv.has("premium"))out.append(String.format(Locale.CHINA,"\n折溢价：%+.2f%%\n计算所用同包成交价：%.3f",iopv.optDouble("premium"),iopv.optDouble("quotePrice")));
    out.append("\n").append(iopv.optString("source")).append("\nIOPV独立更新时间未提供；该源可能延迟，不能认作保证实时。");
   }
  }
  JSONObject estimate=values.optJSONObject("estimate");if(estimate==null)out.append("\n盘中估算净值：暂未提供");else{out.append(String.format(Locale.CHINA,"\n盘中估算净值：%.4f",estimate.optDouble("value")));if(estimate.has("change"))out.append(String.format(Locale.CHINA,"（%+.2f%%）",estimate.optDouble("change")));out.append("\n估值时间：").append(estimate.optString("time")).append("\n第三方估算，不是IOPV或正式净值。");}
  JSONObject nav=values.optJSONObject("nav");if(nav==null)out.append("\n已公布净值：暂未提供");else out.append(String.format(Locale.CHINA,"\n已公布净值：%.4f\n净值日期：%s",nav.optDouble("value"),nav.optString("date")));
  out.append("\n日期早于当前交易日时为历史数据；无估值不推算补齐。折溢价仅使用同一行情包的成交价和IOPV。");return out.toString();
 }
}
