package com.marketdesk;
import java.util.*;
/** User-requested A-class funds. Codes are fund shares, never their target ETF/index codes. */
public final class RequestedFunds {
 public static final class Fund {
  public final String name,code,market,type;
  Fund(String name,String code,String market,String type){this.name=name;this.code=code;this.market=market;this.type=type;}
 }
 public static final List<Fund> FUNDS=Collections.unmodifiableList(Arrays.asList(
  new Fund("华宝纳斯达克精选股票A","017436","美股","主动股票型QDII"),
  new Fund("易方达恒生科技ETF联接A","013308","港股","指数型ETF联接（QDII）"),
  new Fund("国泰A股电网设备ETF联接A","023638","A股","指数型ETF联接"),
  new Fund("富国全球科技互联网股票A","100055","多市场 / 其他","主动股票型QDII（多市场）"),
  new Fund("汇添富国证港股通创新药ETF联接A","021030","港股","指数型ETF联接")
 ));
 public static String append(String current){
  WatchlistTable table=new WatchlistTable(current);Set<String> codes=new HashSet<>();for(WatchlistTable.Row row:table.rows())codes.add(row.code.trim());
  for(Fund fund:FUNDS)if(codes.add("F:"+fund.code)){table.add();WatchlistTable.Row row=table.rows().get(table.rows().size()-1);row.name=fund.name;row.code="F:"+fund.code;}
  return table.encode();
 }
 public static String type(String code){for(Fund fund:FUNDS)if(code.equals("F:"+fund.code))return fund.type;return "";}
}
