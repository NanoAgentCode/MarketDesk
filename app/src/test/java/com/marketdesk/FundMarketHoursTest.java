package com.marketdesk;
import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;
public class FundMarketHoursTest {
 @Test public void usSummerHoursUseNewYorkDst(){String s=FundMarketHours.description("美股",Instant.parse("2026-07-07T12:00:00Z").toEpochMilli());assertTrue(s.contains("21:30—次日04:00"));}
 @Test public void usWinterHoursChangeAutomatically(){String s=FundMarketHours.description("美股",Instant.parse("2026-12-07T12:00:00Z").toEpochMilli());assertTrue(s.contains("22:30—次日05:00"));}
 @Test public void labelsAreNotTradingCalendarClaims(){String s=FundMarketHours.description("港股",0);assertTrue(s.contains("用户备注"));assertTrue(s.contains("不能据此认定当前已开盘"));}
 @Test public void mainlandSessionHasLunchBreak(){assertTrue(FundMarketHours.description("A股",0).contains("09:30—11:30、13:00—15:00"));}
}
