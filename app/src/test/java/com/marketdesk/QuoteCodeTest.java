package com.marketdesk;
import org.junit.Test;
import static org.junit.Assert.*;
public class QuoteCodeTest {
 @Test public void goldEtfFallbackKeepsSameShanghaiCode(){assertEquals("518880.SS",QuoteCode.yahooFallback("1.518880"));}
 @Test public void shenzhenEtfKeepsItsMarket(){assertEquals("159326.SZ",QuoteCode.yahooFallback("0.159326"));}
 @Test public void csiIndexIsNotGuessedAsShanghaiEtf(){assertEquals("",QuoteCode.yahooFallback("2.931250"));}
 @Test public void existingCodesRoundTrip(){for(String code:new String[]{"Y:^IXIC","Y:0700.HK","E:2.931250","E:1.561380","F:000001"})assertEquals(code,QuoteCode.join(QuoteCode.source(code),QuoteCode.body(code)));}
 @Test public void sourceSelectionAddsPrefix(){assertEquals("E:1.561380",QuoteCode.join(1," 1.561380 "));assertEquals("Y:^NDX",QuoteCode.join(0,"^NDX"));}
 @Test public void pastedMatchingPrefixIsNotDuplicated(){assertEquals("E:2.931250",QuoteCode.join(1,"E:2.931250"));}
 @Test public void dropdownCodeSavesToExistingStorageFormat(){WatchlistTable t=new WatchlistTable("国泰ETF|E:1.561380");t.rows().get(0).code=QuoteCode.join(0,"QQQ");assertEquals("国泰ETF|Y:QQQ",t.encode());}
}
