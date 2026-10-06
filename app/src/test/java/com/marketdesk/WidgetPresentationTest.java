package com.marketdesk;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class WidgetPresentationTest {
 @Test public void heightLimitsSameOrderedList(){Map<String,Object> saved=new HashMap<>();saved.put("watch","甲|Y:AAPL\n乙|Y:QQQ\n丙|E:2.931250");List<WidgetPresentation.Row> rows=WidgetPresentation.rows(saved,208,10000);assertEquals(2,rows.size());assertEquals("Y:AAPL",rows.get(0).code);assertEquals("Y:QQQ",rows.get(1).code);}
 @Test public void smallAndLargeWidgetsHaveBoundedCapacity(){assertEquals(1,WidgetPresentation.capacity(100));assertEquals(10,WidgetPresentation.capacity(1000));}
 @Test public void formatsTimePriceAndCacheFromSameSnapshot(){Map<String,Object> saved=new HashMap<>();saved.put("watch","指数|E:2.931250");saved.put("E:2.931250","{\"price\":1243.61,\"change\":4.12,\"time\":\"09-30 15:00\",\"kind\":\"minute\",\"dataTime\":1000,\"received\":1000}");saved.put("error:E:2.931250","失败");WidgetPresentation.Row r=WidgetPresentation.rows(saved,280,100000000).get(0);assertEquals("1243.610",r.price);assertEquals("+4.12%",r.change);assertEquals("09-30 15:00 历史 分时 缓存",r.meta);}
 @Test public void fundEstimatesAreMarked(){Map<String,Object> saved=new HashMap<>();saved.put("watch","基金|F:000001");saved.put("F:000001","{\"price\":1.5,\"change\":-1,\"time\":\"今天\",\"received\":1000}");WidgetPresentation.Row r=WidgetPresentation.rows(saved,280,1000).get(0);assertTrue(r.meta.endsWith("估算"));assertEquals("-1.00%",r.change);assertEquals(0xff41d399,r.color);}
 @Test public void badCacheDoesNotBecomeZeroQuote(){Map<String,Object> saved=new HashMap<>();saved.put("watch","苹果|Y:AAPL");saved.put("Y:AAPL","bad");WidgetPresentation.Row r=WidgetPresentation.rows(saved,280,0).get(0);assertEquals("—",r.price);assertEquals("暂不可用",r.meta);}
 @Test public void savingNewOrderIsReflected(){Map<String,Object> saved=new HashMap<>();saved.put("watch","甲|Y:AAPL\n乙|Y:QQQ");assertEquals("甲",WidgetPresentation.rows(saved,280,0).get(0).name);saved.put("watch","乙改名|Y:QQQ\n甲|Y:AAPL");assertEquals("乙改名",WidgetPresentation.rows(saved,280,0).get(0).name);}
}
