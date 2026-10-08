package com.marketdesk.data;

import java.util.ArrayList;
import java.util.List;

/** Existing watchlist format shared by storage, validation and table editing. */
public final class WatchlistCodec {
  public static final String DEFAULTS =
      "纳斯达克指数|Y:^IXIC\n"
          + "纳斯达克100|Y:^NDX\n"
          + "恒生科技指数|Y:HSTECH.HK\n"
          + "港股通创新药指数|E:2.931250\n"
          + "电网设备ETF 华夏|E:0.159326";

  private WatchlistCodec() {}

  public static List<String[]> items(String text) {
    List<String[]> result = new ArrayList<>();
    for (String line : text.split("\\n")) {
      String[] parts = line.trim().split("\\|", 2);
      if (parts.length == 2) result.add(parts);
    }
    return result;
  }

  public static void validate(String text) {
    if (text.trim().isEmpty()) throw new IllegalArgumentException("至少保留一个自选");
    if (text.trim().split("\\n").length > 20) throw new IllegalArgumentException("最多20个自选");
    for (String line : text.trim().split("\\n")) {
      String[] parts = line.trim().split("\\|", 2);
      if (parts.length != 2
          || parts[0].trim().isEmpty()
          || !parts[1].matches("(Y:[A-Za-z0-9^.=_-]+|E:[0-9]+\\.[A-Za-z0-9]+|F:[0-9]{6})")) {
        throw new IllegalArgumentException("格式错误：" + line);
      }
    }
  }
}
