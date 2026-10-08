package com.marketdesk.network;

import com.marketdesk.data.MarketTime;
import com.marketdesk.data.ReferencePolicy;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Endpoint parameters are kept separate from transport, parsing and source selection. */
public final class MarketUrls {
  public static final List<String> FUND_HOSTS =
      Collections.unmodifiableList(
          Arrays.asList("fundcomapi.tiantianfunds.com", "fundcomapi.eastmoney.com"));
  private static final String DAILY_FIELDS =
      "&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61";
  private static final String DELAY_BATCH =
      "https://push2delay.eastmoney.com/api/qt/ulist.np/get?secids=";

  private MarketUrls() {}

  public static String fund(String host, String code) {
    return "https://"
        + host
        + "/mm/newCore/FundValuationLast?FCODES="
        + code
        + "&FIELDS=FCODE,SHORTNAME,GSZZL,GZTIME,GSZ,NAV,PDATE";
  }

  public static String holdings(String code) {
    return "https://fundmobapi.eastmoney.com/FundMNewApi/FundMNInverstPosition?FCODE="
        + code
        + "&deviceid=marketdesk&plat=Android&product=EFund&version=6.2.8";
  }

  public static String iopv(String secid) {
    return DELAY_BATCH + secid + "&fltt=2&invt=2&fields=f12,f13,f2,f124,f297,f441";
  }

  public static String latestBatch(String secids) {
    return DELAY_BATCH + secids + "&fltt=2&invt=2&fields=f12,f13,f2,f124";
  }

  public static String eastmoneyLatest(String secid) {
    return "https://push2.eastmoney.com/api/qt/stock/get?secid="
        + secid
        + "&fltt=2&invt=2&fields=f43,f60,f170,f59,f86";
  }

  public static String eastmoneyTrends(String secid) {
    return "https://push2his.eastmoney.com/api/qt/stock/trends2/get?secid="
        + secid
        + "&ndays=1&iscr=0&fields1=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11&fields2=f51,f52,f53,f54,f55,f56,f57,f58";
  }

  public static String eastmoneyDaily(String secid) {
    return "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid="
        + secid
        + "&klt=101&fqt=0&lmt=2&end=20500101"
        + DAILY_FIELDS;
  }

  public static String stockHistory(String secid, String date, long now) {
    String begin =
        LocalDate.parse(date)
            .minusDays(ReferencePolicy.BASELINE_GAP_DAYS)
            .format(DateTimeFormatter.BASIC_ISO_DATE);
    String end = MarketTime.today(now).format(DateTimeFormatter.BASIC_ISO_DATE);
    return "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid="
        + secid
        + "&klt=101&fqt=1&beg="
        + begin
        + "&end="
        + end
        + DAILY_FIELDS;
  }

  public static String yahooQuote(String symbol) throws Exception {
    return yahooBase(symbol) + "?interval=1d&range=1d";
  }

  public static String yahooHistory(String symbol, String date, long now) throws Exception {
    long start =
        LocalDate.parse(date)
            .minusDays(ReferencePolicy.BASELINE_GAP_DAYS)
            .atStartOfDay(ZoneId.of("UTC"))
            .toEpochSecond();
    return yahooBase(symbol)
        + "?interval=1d&period1="
        + start
        + "&period2="
        + (now / 1000 + 86400)
        + "&events=div%2Csplits";
  }

  private static String yahooBase(String symbol) throws Exception {
    return "https://query1.finance.yahoo.com/v8/finance/chart/"
        + URLEncoder.encode(symbol, "UTF-8");
  }

  public static String tencent(String symbol) {
    return "https://qt.gtimg.cn/q=" + symbol;
  }
}
