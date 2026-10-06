package com.marketdesk;
import org.junit.Test;
import static org.junit.Assert.*;
public class WatchlistTableTest {
 @Test public void preservesExistingCodesAndOrder(){String text="恒生科技|Y:HSTECH.HK\n创新药|E:2.931250";assertEquals(text,new WatchlistTable(text).encode());}
 @Test public void editsColumnsWithoutRequiringDelimiter(){WatchlistTable t=new WatchlistTable("旧名称|Y:AAPL");t.rows().get(0).name=" 腾讯 ";t.rows().get(0).code=" Y:0700.HK ";assertEquals("腾讯|Y:0700.HK",t.encode());}
 @Test public void movingThenRemovingPreservesCorrectRows(){WatchlistTable t=new WatchlistTable("苹果|Y:AAPL\n腾讯|Y:0700.HK\n指数|E:2.931250");t.move(2,-1);t.remove(0);assertEquals("指数|E:2.931250\n腾讯|Y:0700.HK",t.encode());}
 @Test public void editsSurviveAddingRow(){WatchlistTable t=new WatchlistTable("苹果|Y:AAPL");t.rows().get(0).name="苹果公司";t.add();t.rows().get(1).name="基金";t.rows().get(1).code="F:000001";assertEquals("苹果公司|Y:AAPL\n基金|F:000001",t.encode());}
 @Test(expected=IllegalArgumentException.class) public void preventsSavingZeroRows(){WatchlistTable t=new WatchlistTable("苹果|Y:AAPL");t.remove(0);t.encode();}
 @Test public void invalidCodeIdentifiesItsRow(){WatchlistTable t=new WatchlistTable("苹果|Y:AAPL");t.add();t.rows().get(1).name="腾讯";t.rows().get(1).code="0700.HK";try{t.encode();fail("Must reject missing prefix");}catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("第2行"));}}
 @Test(expected=IllegalArgumentException.class) public void nameCannotInjectAnotherStoredRow(){WatchlistTable t=new WatchlistTable("苹果|Y:AAPL");t.rows().get(0).name="苹果\n腾讯|Y:0700.HK";t.encode();}
 @Test(expected=IllegalArgumentException.class) public void cannotExceedTwentyRows(){WatchlistTable t=new WatchlistTable("");for(int i=0;i<21;i++)t.add();}
}
