package com.marketdesk.data;

import com.marketdesk.FundReferenceData;
import com.marketdesk.network.HttpTransport;
import com.marketdesk.network.UrlConnectionTransport;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/** Lazy composition root. Repositories never call the Quotes/FundData Android-facing facades. */
public final class MarketServices {
  private MarketServices() {}

  private static final class Holder {
    static final HttpTransport HTTP = new UrlConnectionTransport();
    static final LongSupplier CLOCK = System::currentTimeMillis;
    static final FundReferenceData REFERENCES =
        new FundReferenceData(
            HTTP, CLOCK, Executors.newFixedThreadPool(ReferencePolicy.REQUEST_THREADS));
    static final FundRepository FUNDS = new FundRepository(HTTP, CLOCK, REFERENCES::reference);
    static final MarketDataSource SOURCE = new MarketDataSource(HTTP, CLOCK, FUNDS::monitored);
    static final QuoteRepository QUOTES = new QuoteRepository(SOURCE::fetch, FUNDS::etf, CLOCK);
  }

  public static HttpTransport http() {
    return Holder.HTTP;
  }

  public static FundRepository funds() {
    return Holder.FUNDS;
  }

  public static MarketDataSource source() {
    return Holder.SOURCE;
  }

  public static QuoteRepository quotes() {
    return Holder.QUOTES;
  }
}
