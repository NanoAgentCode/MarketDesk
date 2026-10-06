package com.marketdesk;
import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
public class RefreshWorker extends Worker {
 public RefreshWorker(Context c,WorkerParameters p){super(c,p);}
 @Override public Result doWork(){Quotes.refresh(getApplicationContext());return Result.success();}
 static Constraints network(){return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();}
 public static void schedule(Context c){WorkManager.getInstance(c).enqueueUniquePeriodicWork("quotes-periodic",ExistingPeriodicWorkPolicy.KEEP,new PeriodicWorkRequest.Builder(RefreshWorker.class,15,TimeUnit.MINUTES).setConstraints(network()).build());}
 public static void now(Context c){WorkManager.getInstance(c).enqueueUniqueWork("quotes-now",ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(RefreshWorker.class).setConstraints(network()).build());}
 // An in-progress refresh may have captured the old codes. Always fetch again after Save.
 public static void afterEdit(Context c){WorkManager.getInstance(c).enqueueUniqueWork("quotes-now",ExistingWorkPolicy.APPEND_OR_REPLACE,new OneTimeWorkRequest.Builder(RefreshWorker.class).setConstraints(network()).build());}
}
