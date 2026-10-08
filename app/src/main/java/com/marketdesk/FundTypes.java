package com.marketdesk;

/** Display metadata for known fund shares, independent of watchlist editing or import. */
public final class FundTypes {
  private FundTypes() {}

  public static String type(String code) {
    switch (code) {
      case "F:017436":
        return "主动股票型QDII";
      case "F:013308":
        return "指数型ETF联接（QDII）";
      case "F:023638":
      case "F:021030":
        return "指数型ETF联接";
      case "F:100055":
        return "主动股票型QDII（多市场）";
      default:
        return "";
    }
  }
}
