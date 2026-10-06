package com.marketdesk;
/** Accepting a launcher request is not confirmation that a widget was pinned. */
public final class PinFeedback {
 public enum State { REJECTED, WAITING, UNCONFIRMED, CONFIRMED }
 public static State resolve(boolean accepted,boolean confirmed,long elapsedMillis){
  if(confirmed)return State.CONFIRMED;if(!accepted)return State.REJECTED;
  return elapsedMillis<10000?State.WAITING:State.UNCONFIRMED;
 }
 public static boolean hasNewWidget(int[] before,int[] after){for(int id:after){boolean existed=false;for(int old:before)if(old==id){existed=true;break;}if(!existed)return true;}return false;}
}
