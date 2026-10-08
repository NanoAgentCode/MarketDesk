package com.marketdesk.data;

import com.marketdesk.ui.QuotePresentation;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/** Collects one complete refresh batch before Android storage publishes it. */
public final class QuoteRefreshBatch {
  public static final class Result {
    public final Map<String, String> quotes;
    public final Map<String, String> errors;

    private Result(Map<String, String> quotes, Map<String, String> errors) {
      this.quotes = Collections.unmodifiableMap(new HashMap<>(quotes));
      this.errors = Collections.unmodifiableMap(new HashMap<>(errors));
    }
  }

  private final QuoteRepository.Source source;
  private final BiConsumer<String, Exception> onFailure;

  public QuoteRefreshBatch(QuoteRepository.Source source, BiConsumer<String, Exception> onFailure) {
    this.source = source;
    this.onFailure = onFailure;
  }

  public Result fetch(List<String[]> items) {
    ExecutorService pool = Executors.newFixedThreadPool(4);
    List<Future<?>> jobs = new ArrayList<>();
    Map<String, String> quotes = new ConcurrentHashMap<>(), errors = new ConcurrentHashMap<>();
    for (String[] item : items) {
      jobs.add(
          pool.submit(
              () -> {
                try {
                  quotes.put(item[1], source.fetch(item[1]).toString());
                } catch (Exception unavailable) {
                  errors.put(item[1], QuotePresentation.errorText(unavailable));
                  onFailure.accept(item[1], unavailable);
                }
              }));
    }
    for (Future<?> job : jobs) {
      try {
        job.get();
      } catch (Exception ignored) {
      }
    }
    pool.shutdown();
    return new Result(quotes, errors);
  }
}
