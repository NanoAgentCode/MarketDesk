package com.marketdesk;
import org.json.JSONObject;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
public final class TencentParser {
 public static JSONObject parse(String raw,String symbol) throws Exception {
  String prefix="v_"+symbol+"=";int start=raw.indexOf(prefix);if(start<0)throw new IOException("返回代码不匹配");
  start=raw.indexOf('"',start)+1;int end=raw.indexOf('"',start);if(start==0||end<start)throw new IOException("行情格式异常");String[] f=raw.substring(start,end).split("~",-1);
  if(f.length<33)throw new IOException("暂无腾讯行情");String expected=symbol.substring(2);if(symbol.equals("usIXIC"))expected=".IXIC";if(symbol.equals("usNDX"))expected=".NDX";
  if(!expected.equalsIgnoreCase(f[2]))throw new IOException("返回代码不匹配");
  double price=Double.parseDouble(f[3]),previous=Double.parseDouble(f[4]),change=Double.parseDouble(f[32]);if(price<=0||previous<=0||!Double.isFinite(price)||!Double.isFinite(change))throw new IOException("行情数值无效");
  String pattern=f[30].contains("/")?"yyyy/MM/dd HH:mm:ss":f[30].contains("-")?"yyyy-MM-dd HH:mm:ss":"yyyyMMddHHmmss";
  ZoneId zone=ZoneId.of(symbol.startsWith("us")?"America/New_York":"Asia/Shanghai");long time=LocalDateTime.parse(f[30],DateTimeFormatter.ofPattern(pattern)).atZone(zone).toInstant().toEpochMilli();
  return new JSONObject().put("price",price).put("change",change).put("dataTime",time).put("source","腾讯财经 · 可能延迟").put("unit",symbol.startsWith("us")?"USD":symbol.startsWith("hk")?"HKD":"CNY").put("kind","quote");
 }
}
