package com.marketdesk;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class QuoteSourcesTest {
 @Test public void goldEtfUsesSameSecurityAcrossThreeProviders(){Map<String,String> plan=QuoteSources.plan("E:1.518880");assertEquals(3,plan.size());assertEquals("Y:518880.SS",plan.get("yahoo"));assertEquals("T:sh518880",plan.get("tencent"));}
 @Test public void selectingYahooStillVerifiesWithOtherSources(){Map<String,String> plan=QuoteSources.plan("Y:561380.SS");assertEquals("E:1.561380",plan.get("eastmoney"));assertEquals("T:sh561380",plan.get("tencent"));}
 @Test public void csiInnovationIndexIsNotReplacedByEtf(){Map<String,String> plan=QuoteSources.plan("E:2.931250");assertEquals(1,plan.size());assertEquals("E:2.931250",plan.get("eastmoney"));}
 @Test public void nasdaq100UsesIndexNotQqq(){assertEquals("T:usNDX",QuoteSources.plan("Y:^NDX").get("tencent"));}
 @Test public void indexUnitsAreNormalizedToPoints(){assertEquals("点",QuoteSources.unit("Y:HSTECH.HK"));assertEquals("点",QuoteSources.unit("Y:^IXIC"));}
 @Test public void fundEstimateIsNotComparedToEtfPrice(){assertEquals(1,QuoteSources.plan("F:000001").size());}
}
