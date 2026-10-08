package com.marketdesk.data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MarketTime {
  public static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
  private static final DateTimeFormatter DISPLAY =
      DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA);

  private MarketTime() {}

  public static LocalDate today(long now) {
    return Instant.ofEpochMilli(now).atZone(BEIJING).toLocalDate();
  }

  public static String beijing(long time) {
    return Instant.ofEpochMilli(time).atZone(BEIJING).format(DISPLAY);
  }

  public static ZoneId stockZone(String secid) {
    return ZoneId.of(
        secid.startsWith("105.") || secid.startsWith("106.") || secid.startsWith("107.")
            ? "America/New_York"
            : "Asia/Shanghai");
  }
}
