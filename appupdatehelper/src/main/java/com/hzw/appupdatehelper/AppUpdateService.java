package com.hzw.appupdatehelper;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * 用于app更新下载的服务
 * Created by 何志伟 on 2017/11/2.
 */
public class AppUpdateService extends Service {

    static final String IS_NOTIFY = "IsNotify";
    static final String START = "StartPoint";

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            long start = intent.getLongExtra(START, 0);
            sendNotification(intent);
            AppUpdateHelper.getInstance()
                    .downloadApk(getApplicationContext(), start);
        }
        return START_REDELIVER_INTENT;
    }

    @Override public void onDestroy() {
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendNotification(Intent intent) {
        if (!intent.getBooleanExtra(IS_NOTIFY, true)) return;
        Notification notification = AppUpdateHelper.getInstance()
                .getNotifyBuilder(getApplicationContext())
                .build();
        startForeground(AppUpdateHelper.NOTIFY_ID, notification);
    }

    //private void releaseService() {
    //    stopForeground(true);
    //    AppUpdateHelper.getInstance()
    //            .cancelNotify();
    //    stopSelf();
    //}

}
