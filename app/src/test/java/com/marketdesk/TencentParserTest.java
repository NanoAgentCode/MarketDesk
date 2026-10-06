package com.marketdesk;
import org.junit.Test;
import org.json.JSONObject;
import java.time.Instant;
import static org.junit.Assert.*;
public class TencentParserTest {
 private String raw(String symbol,String security,String time){String[] f=new String[33];java.util.Arrays.fill(f,"0");f[2]=security;f[3]="8.633";f[4]="8.519";f[30]=time;f[32]="1.34";return "v_"+symbol+"=\""+String.join("~",f)+"\";";}
 @Test public void mainlandEtfPreservesDecimalsAndShanghaiTime() throws Exception {JSONObject q=TencentParser.parse(raw("sh518880","518880","20260930161435"),"sh518880");assertEquals(8.633,q.getDouble("price"),0);assertEquals(1.34,q.getDouble("change"),0);assertEquals(Instant.parse("2026-09-30T08:14:35Z").toEpochMilli(),q.getLong("dataTime"));}
 @Test public void usIndexUsesNewYorkDstNotPhoneTimezone() throws Exception {JSONObject q=TencentParser.parse(raw("usNDX",".NDX","2026-10-05 17:15:59"),"usNDX");assertEquals(Instant.parse("2026-10-05T21:15:59Z").toEpochMilli(),q.getLong("dataTime"));}
 @Test public void hangSengDateFormatIsSupported() throws Exception {JSONObject q=TencentParser.parse(raw("hkHSTECH","HSTECH","2026/10/06 16:08:26"),"hkHSTECH");assertEquals(Instant.parse("2026-10-06T08:08:26Z").toEpochMilli(),q.getLong("dataTime"));}
 @Test(expected=Exception.class) public void wrongSecurityRejected() throws Exception {TencentParser.parse(raw("sh518880","561380","20260930161435"),"sh518880");}
 @Test(expected=Exception.class) public void noDataRejected() throws Exception {TencentParser.parse("v_sh518880=\"\";","sh518880");}
}
