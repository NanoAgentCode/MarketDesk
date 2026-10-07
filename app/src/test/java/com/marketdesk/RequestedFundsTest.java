package com.marketdesk;
import org.junit.Test;
import static org.junit.Assert.*;
public class RequestedFundsTest {
 @Test public void keepsExistingWatchlistAndAddsFiveFundShares(){String old="纳指|Y:^IXIC\n国泰ETF|E:1.561380";String added=RequestedFunds.append(old);assertTrue(added.startsWith(old+"\n"));assertTrue(added.contains("F:017436"));assertTrue(added.contains("F:023638"));assertTrue(added.contains("F:021030"));assertEquals(7,new WatchlistTable(added).rows().size());}
 @Test public void secondImportDoesNotDuplicateOrRenameExistingFunds(){String old="自定义联接名称|F:013308";String added=RequestedFunds.append(old);assertEquals(added,RequestedFunds.append(added));assertTrue(added.startsWith(old));assertEquals(5,new WatchlistTable(added).rows().size());}
 @Test public void linkFundsAreNotMisclassifiedAsActive(){assertTrue(RequestedFunds.type("F:023638").contains("指数型"));assertTrue(RequestedFunds.type("F:017436").contains("主动"));}
 @Test public void tooManyRowsNeverPartiallyPersist(){StringBuilder old=new StringBuilder();for(int i=0;i<17;i++){if(i>0)old.append('\n');old.append("股票").append(i).append("|Y:TEST").append(i);}try{RequestedFunds.append(old.toString());fail("Must reject over limit");}catch(IllegalArgumentException expected){assertEquals(17,new WatchlistTable(old.toString()).rows().size());}}
}
