package com.marketdesk;
import org.json.*;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
public final class FundValuation {
 public static boolean positive(double value){return Double.isFinite(value)&&value>0;}
 public static JSONObject parse(String raw,String code) throws Exception {
  JSONObject root=new JSONObject(raw);if(!root.optBoolean("success",true)||root.optInt("errorCode",0)!=0)throw new IOException("基金数据接口返回失败");
  JSONArray rows=root.getJSONArray("data");JSONObject found=null;for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(code.equals(row.optString("FCODE"))){found=row;break;}}
  if(found==null)throw new IOException("未找到该基金的数据");JSONObject result=new JSONObject().put("code",code).put("name",found.optString("SHORTNAME"));
  double nav=found.optDouble("NAV",Double.NaN);String date=found.optString("PDATE","");
  if(positive(nav)&&date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")){long time=LocalDate.parse(date).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();result.put("nav",new JSONObject().put("value",nav).put("date",date).put("dataTime",time).put("source","天天基金 · 已公布净值"));}
  double estimate=found.optDouble("GSZ",Double.NaN);String at=found.optString("GZTIME","");
  if(positive(estimate)&&at.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}(:[0-9]{2})?")){
   long time=LocalDateTime.parse(at,DateTimeFormatter.ofPattern(at.length()==16?"yyyy-MM-dd HH:mm":"yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
   JSONObject value=new JSONObject().put("value",estimate).put("time",at).put("dataTime",time).put("source","天天基金 · 第三方估算");double change=found.optDouble("GSZZL",Double.NaN);if(Double.isFinite(change))value.put("change",change);result.put("estimate",value);
  }
  return result;
 }
 public static JSONObject quote(JSONObject values,long now) throws Exception {
  boolean estimated=values.has("estimate");JSONObject value=values.optJSONObject(estimated?"estimate":"nav");if(value==null)throw new IOException("该基金暂未提供估值或已公布净值");
  JSONObject q=new JSONObject().put("price",value.getDouble("value")).put("unit",estimated?"估算净值":"已公布净值").put("kind",estimated?"estimate":"nav").put("time",estimated?value.getString("time"):value.getString("date")+" 净值").put("dataTime",value.getLong("dataTime")).put("received",now).put("source",value.getString("source")).put("origin","fund");
  if(value.has("change"))q.put("change",value.getDouble("change"));
  JSONObject reference=values.optJSONObject("holdingsReference");boolean hasReference=reference!=null&&reference.has("contribution");
  values.put("type","fund");return q.put("fundMetrics",values).put("verification","single").put("verificationLabel",estimated?"单源基金估算":hasReference?"已公布净值；持仓参考见更多":"已公布净值，暂无盘中估值").put("verificationShort",estimated?"单源":hasReference?"参考":"净值").put("verifiedAt",now);
 }
}
