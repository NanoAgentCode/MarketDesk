package com.marketdesk;
import org.json.*;
import java.io.IOException;
public final class EtfReference {
 public static String secid(String code){if(code.matches("E:1\\.(51|56|58)[0-9]{4}")||code.matches("E:0\\.159[0-9]{3}"))return code.substring(2);
  if(code.matches("Y:(51|56|58)[0-9]{4}\\.SS"))return "1."+code.substring(2,8);if(code.matches("Y:159[0-9]{3}\\.SZ"))return "0."+code.substring(2,8);return "";}
 public static JSONObject parse(String raw,String secid) throws Exception {
  JSONObject root=new JSONObject(raw);if(root.optInt("rc",0)!=0||root.isNull("data"))throw new IOException("暂无ETF参考净值数据");JSONArray rows=root.getJSONObject("data").getJSONArray("diff");
  JSONObject found=null;for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(secid.equals(row.optInt("f13",-1)+"."+row.optString("f12"))){found=row;break;}}
  if(found==null)throw new IOException("返回的ETF代码不匹配");double value=found.optDouble("f441",Double.NaN);if(!FundValuation.positive(value))throw new IOException("该ETF暂无可用IOPV");
  JSONObject result=new JSONObject().put("value",value).put("source","东方财富延迟行情 · IOPV字段").put("packageTime",found.optLong("f124",0)*1000).put("packageDate",found.optString("f297",""));
  double price=found.optDouble("f2",Double.NaN);if(FundValuation.positive(price)){result.put("quotePrice",price);result.put("premium",(price/value-1)*100);}
  return result;
 }
}
