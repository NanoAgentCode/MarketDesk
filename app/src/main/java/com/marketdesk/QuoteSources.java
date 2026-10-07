package com.marketdesk;
import java.util.*;
public final class QuoteSources {
 public static Map<String,String> plan(String code){
  Map<String,String> sources=new LinkedHashMap<>();sources.put(provider(code),code);
  if(code.startsWith("F:"))return sources;
  if(code.equals("Y:^IXIC")){sources.put("tencent","T:usIXIC");return sources;}
  if(code.equals("Y:^NDX")){sources.put("tencent","T:usNDX");return sources;}
  if(code.equals("Y:HSTECH.HK")){sources.put("tencent","T:hkHSTECH");return sources;}
  if(code.matches("E:[01]\\.[0-9]{6}")){String secid=code.substring(2);sources.put("yahoo","Y:"+QuoteCode.yahooFallback(secid));sources.put("tencent","T:"+(secid.startsWith("1.")?"sh":"sz")+secid.substring(2));}
  else if(code.matches("Y:[0-9]{6}\\.(SS|SZ)")){boolean sh=code.endsWith(".SS");String symbol=code.substring(2,8);sources.put("eastmoney","E:"+(sh?"1.":"0.")+symbol);sources.put("tencent","T:"+(sh?"sh":"sz")+symbol);}
  else if(code.matches("Y:[0-9]{4,5}\\.HK")){String symbol=code.substring(2,code.length()-3);symbol=String.format(Locale.ROOT,"%05d",Integer.parseInt(symbol));sources.put("eastmoney","E:116."+symbol);sources.put("tencent","T:hk"+symbol);}
  else if(code.matches("Y:[A-Z][A-Z0-9.-]{0,9}"))sources.put("tencent","T:us"+code.substring(2));
  return sources;
 }
 public static String provider(String code){return code.startsWith("Y:")?"yahoo":code.startsWith("E:")?"eastmoney":code.startsWith("T:")?"tencent":"fund";}
 public static String unit(String code){if(Arrays.asList("Y:^IXIC","Y:^NDX","Y:^GSPC","Y:^DJI","Y:^HSI","Y:^HSCE","Y:HSTECH.HK","E:2.931250","E:1.000001","E:0.399001","E:1.000300","E:0.399006","Y:000001.SS","Y:399001.SZ","Y:000300.SS","Y:399006.SZ").contains(code))return "点";if(code.matches("E:[01]\\.[0-9]{6}")||code.matches("Y:[0-9]{6}\\.(SS|SZ)"))return "CNY";if(code.matches("Y:[0-9]{4,5}\\.HK"))return "HKD";return "";}
}
