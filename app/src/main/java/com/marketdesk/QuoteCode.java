package com.marketdesk;
public final class QuoteCode {
 private static final String[] SOURCES={"Y","E","F"};
 public static int source(String code){for(int i=0;i<SOURCES.length;i++)if(code.startsWith(SOURCES[i]+":"))return i;return 0;}
 public static String body(String code){return code.matches("^[YEF]:.*")?code.substring(2):code;}
 public static String join(int source,String body){String prefix=SOURCES[source]+":";String text=body.trim();return text.startsWith(prefix)?text:prefix+text;}
 public static String yahooFallback(String secid){if(secid.matches("1\\.[0-9]{6}"))return secid.substring(2)+".SS";if(secid.matches("0\\.[0-9]{6}"))return secid.substring(2)+".SZ";return "";}
}
