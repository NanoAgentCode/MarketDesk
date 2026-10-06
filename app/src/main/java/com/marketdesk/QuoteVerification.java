package com.marketdesk;
import org.json.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
public final class QuoteVerification {
 public static JSONObject combine(List<JSONObject> quotes,JSONArray failures,long now) throws Exception {
  Map<String,JSONObject> unique=new LinkedHashMap<>();for(JSONObject q:quotes){if(!q.has("origin")||!q.has("price")||!q.has("change"))continue;double price=q.optDouble("price"),change=q.optDouble("change");if(price<=0||!Double.isFinite(price)||!Double.isFinite(change))continue;String id=q.getString("origin");JSONObject old=unique.get(id);if(old==null||old.optLong("dataTime")<q.optLong("dataTime"))unique.put(id,q);}
  if(unique.isEmpty())throw new IOException("全部行情源不可用");
  List<JSONObject> available=new ArrayList<>(unique.values());JSONObject selected=available.get(0);for(JSONObject q:available)if(q.optLong("dataTime")>selected.optLong("dataTime"))selected=q;
  boolean mismatch=false,unaligned=false;int pairs=0;
  for(int i=0;i<available.size();i++)for(int j=i+1;j<available.size();j++){JSONObject a=available.get(i),b=available.get(j);long ta=a.optLong("dataTime"),tb=b.optLong("dataTime");
   boolean oldSameDay=ta>0&&tb>0&&now-ta>86400000L&&now-tb>86400000L&&Instant.ofEpochMilli(ta).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().equals(Instant.ofEpochMilli(tb).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate());
   if(ta<=0||tb<=0||!a.optString("unit").equals(b.optString("unit"))||!oldSameDay&&Math.abs(ta-tb)>120000){unaligned=true;continue;}
   pairs++;double relative=Math.abs(a.getDouble("price")-b.getDouble("price"))/Math.max(a.getDouble("price"),b.getDouble("price"));
   if(relative>0.001||Math.abs(a.getDouble("change")-b.getDouble("change"))>0.05)mismatch=true;
  }
  String state=available.size()==1?"single":mismatch?"different":unaligned||pairs==0?"unaligned":"agree";
  String label=state.equals("single")?"单源可用":state.equals("different")?"多源有差异":state.equals("unaligned")?"行情时间或口径不齐":available.size()==2?"双源一致":"多源一致";
  JSONArray samples=new JSONArray();for(JSONObject q:available)samples.put(new JSONObject(q.toString()));
  return new JSONObject(selected.toString()).put("verification",state).put("verificationLabel",label).put("verificationShort",state.equals("single")?"单源":state.equals("different")?"差异":state.equals("unaligned")?"待核对":"已核对").put("verificationSamples",samples).put("verificationFailures",failures).put("verifiedAt",now);
 }
}
