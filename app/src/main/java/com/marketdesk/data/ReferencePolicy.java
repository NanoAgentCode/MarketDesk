package com.marketdesk.data;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/** Existing reference eligibility and cache limits in one place. */
public final class ReferencePolicy {
  public static final long DAY_MS = TimeUnit.DAYS.toMillis(1);
  public static final long PRICE_CACHE_MS = TimeUnit.SECONDS.toMillis(60);
  public static final long DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(40);
  public static final long FUTURE_TOLERANCE_MS = TimeUnit.MINUTES.toMillis(5);
  public static final int MAX_NAV_AGE_DAYS = 45;
  public static final int MAX_REPORT_AGE_DAYS = 190;
  public static final int BASELINE_GAP_DAYS = 7;
  public static final int MAX_PRICE_AGE_DAYS = 14;
  public static final int MAX_TIMESTAMP_AGE_DAYS = 15;
  public static final int REQUEST_THREADS = 4;
  public static final int CACHE_ENTRIES = 512;

  private ReferencePolicy() {}

  public static void validatePeriod(LocalDate base, LocalDate target, String navDate, long now)
      throws IOException {
    LocalDate nav = LocalDate.parse(navDate);
    if (base == null
        || target == null
        || base.isAfter(nav)
        || ChronoUnit.DAYS.between(base, nav) > BASELINE_GAP_DAYS) {
      throw new IOException("缺少净值日期对应的历史价格");
    }
    if (target.isBefore(nav)
        || target.isAfter(MarketTime.today(now))
        || ChronoUnit.DAYS.between(target, MarketTime.today(now)) > MAX_PRICE_AGE_DAYS) {
      throw new IOException("行情日期过旧或异常");
    }
  }
}
