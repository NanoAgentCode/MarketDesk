package com.marketdesk;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
public class LiveService extends Service {
 public static volatile boolean running;
 private ScheduledExecutorService timer; private long end;
 public void onCreate(){super.onCreate();running=true;NotificationManager n=getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel("live","行情盯盘",NotificationManager.IMPORTANCE_LOW));}
 public int onStartCommand(Intent intent,int flags,int id){
  if("STOP".equals(intent==null?null:intent.getAction())){stopSelf();return START_NOT_STICKY;}
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,LiveService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
  startForeground(8,new Notification.Builder(this,"live").setSmallIcon(android.R.drawable.ic_menu_recent_history).setContentTitle("行情盯盘中 · 每60秒刷新").setContentText("两小时后自动结束，行情可能延迟").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"结束",stop).build()).build());
  if(timer==null){end=System.currentTimeMillis()+2*60*60*1000;getSharedPreferences("market",0).edit().putLong("live_until",end).apply();timer=Executors.newSingleThreadScheduledExecutor();timer.scheduleWithFixedDelay(()->{if(System.currentTimeMillis()>=end){stopSelf();return;}Quotes.refresh(this);},0,60,TimeUnit.SECONDS);}
  return START_NOT_STICKY;
 }
 public void onTimeout(int startId,int fgsType){stopSelf();}
 public void onDestroy(){running=false;getSharedPreferences("market",0).edit().remove("live_until").apply();if(timer!=null)timer.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
 public IBinder onBind(Intent i){return null;}
}
