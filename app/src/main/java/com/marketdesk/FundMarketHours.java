package com.marketdesk;
import java.time.*;
import java.time.format.DateTimeFormatter;
public final class FundMarketHours {
 public static String description(String market,long now){
  String hours;
  if("A股".equals(market))hours="通常北京时间09:30—11:30、13:00—15:00";
  else if("港股".equals(market))hours="通常北京时间09:30—12:00、13:00—16:00";
  else if("美股".equals(market)){
   ZoneId ny=ZoneId.of("America/New_York"),beijing=ZoneId.of("Asia/Shanghai");LocalDate day=Instant.ofEpochMilli(now).atZone(ny).toLocalDate();
   ZonedDateTime open=day.atTime(9,30).atZone(ny).withZoneSameInstant(beijing),close=day.atTime(16,0).atZone(ny).withZoneSameInstant(beijing);DateTimeFormatter f=DateTimeFormatter.ofPattern("HH:mm");
   hours="美股常规时段通常北京时间"+f.format(open)+"—"+(close.toLocalDate().isAfter(open.toLocalDate())?"次日":"")+f.format(close)+"（随夏令时变化）";
  }else hours="多市场基金的估值取决于各市场及基金估值口径";
  return "主要投资市场："+(market==null||market.isEmpty()?"未备注":market)+"（用户备注）\n"+hours+"\n时段说明不含节假日、临时休市及盘前盘后，不能据此认定当前已开盘。QDII正式净值可能滞后；开盘也不代表平台会提供估值。";
 }
}
