package com.marketdesk.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** The app's existing HTTP policy, independent of Android storage and financial calculations. */
public final class UrlConnectionTransport implements HttpTransport {
  private static final int TIMEOUT_MS = 6000;

  @Override
  public String get(String url, String charset) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(TIMEOUT_MS);
    connection.setReadTimeout(TIMEOUT_MS);
    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
    if (connection.getURL().getHost().endsWith(".eastmoney.com")) {
      connection.setRequestProperty("Referer", "https://quote.eastmoney.com/");
    }
    try {
      if (connection.getResponseCode() != 200) {
        throw new IOException("HTTP " + connection.getResponseCode());
      }
      try (InputStream input = connection.getInputStream();
          ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) != -1) {
          output.write(buffer, 0, length);
        }
        return output.toString(charset);
      }
    } finally {
      connection.disconnect();
    }
  }
}
