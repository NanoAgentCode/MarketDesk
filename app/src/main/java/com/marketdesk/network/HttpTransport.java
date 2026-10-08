package com.marketdesk.network;

/** Transport boundary shared by market and fund sources; tests can inject recorded responses. */
@FunctionalInterface
public interface HttpTransport {
  String get(String url, String charset) throws Exception;

  default String get(String url) throws Exception {
    return get(url, "UTF-8");
  }
}
