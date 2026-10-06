package com.marketdesk;
public final class RefreshInterval {
 public static int normalize(int seconds){return seconds==15||seconds==30||seconds==60||seconds==120?seconds:30;}
}
