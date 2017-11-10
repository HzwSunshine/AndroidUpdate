package com.hzw.appupdatehelper;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

/**
 * 用于app更新下载的服务
 * Created by 何志伟 on 2017/11/2.
 */
public class AppUpdateService extends Service {

    static final String ICON_COLOR = "IconColor";
    static final String FILE_PATH = "FilePath";
    static final String IS_NOTIFY = "IsNotify";
    static final String DN_TIPS = "DownloadTips";
    static final String L_ICON = "LargeIcon";
    static final String S_ICON = "SmallIcon";
    static final String START = "StartPoint";
    static final String TITLE = "Title";
    static final String TIPS = "Tips";
    static final String URL = "Apk_Url";
    private NotificationCompat.Builder builder;
    private NotificationManager mManager;
    private UpdateListener progress;
    private String downloadTips;
    private final int ID = 1314;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new UpdateBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String url = intent.getStringExtra(URL);
            String filePath = intent.getStringExtra(FILE_PATH);
            long start = intent.getLongExtra(START, 0);
            downloadTips = intent.getStringExtra(DN_TIPS);
            downloadTips = downloadTips == null ? "正在下载" : downloadTips;
            if (mManager != null) startForeground(ID, builder.build());
            sendNotification(intent);
            downloadApk(url, filePath, start);
        }
        return START_REDELIVER_INTENT;
    }

    class UpdateBinder extends Binder {
        AppUpdateService getService() {
            return AppUpdateService.this;
        }
    }

    public void setUpdateProgress(UpdateListener progress) {
        this.progress = progress;
    }

    private void downloadApk(final String url, final String filePath, long startPoint) {
        if (AppUpdateHelper.getInstance().isExecute(url)) return;
        boolean isPointDownload = startPoint != AppUpdateHelper.NO_POINT_FLAG;
        AppUpdateHelper.getInstance().downloadApk(url, startPoint,
                new DownloadListener(filePath, getApplicationContext(), isPointDownload) {
                    @Override
                    public void progress(long currentBytes, long totalBytes, float per) {
                        if (progress != null) progress.progress(currentBytes, totalBytes, per);
                        if (currentBytes == totalBytes) {
                            //下载完成，安装apk，释放资源
                            AppUpdateUtil.installApk(getApplicationContext(), filePath);
                            AppUpdateHelper.getInstance().release(getApplication());
                            stopForeground(true);
                            downloadTips = null;
                            stopSelf();
                            release();
                        }
                    }

                    @Override
                    public void threadProgress(long currentBytes, long totalBytes, float per) {
                        if (progress != null) {
                            progress.threadProgress(currentBytes, totalBytes, per);
                        }
                        if (mManager != null) {
                            int len = (int) (per * 100);
                            builder.setProgress(100, len, false);
                            builder.setContentText(String.format("%s%s%%", downloadTips, len));
                            mManager.notify(ID, builder.build());
                        }
                    }

                    @Override
                    public void error(int errorType) {
                        if (progress != null) progress.error(errorType);
                        if (mManager != null) {
                            stopForeground(true);
                            mManager.cancel(ID);
                        }
                    }
                });
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        super.unbindService(conn);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        progress = null;
    }


    private void sendNotification(Intent intent) {
        if (!intent.getBooleanExtra(IS_NOTIFY, true)) return;
        if (mManager != null) return;
        mManager = (NotificationManager) getApplication().getSystemService(Context.NOTIFICATION_SERVICE);
        builder = new NotificationCompat.Builder(this, "AppUpdate");
        builder.setWhen(System.currentTimeMillis());
        builder.setAutoCancel(false);//禁止用户点击删除按钮删除
        builder.setOngoing(true);//禁止滑动删除
        builder.setShowWhen(true);//右上角的时间显示
        int color = intent.getIntExtra(ICON_COLOR, 0);
        if (color != 0) builder.setColor(Color.RED);
        String tips = intent.getStringExtra(TIPS);
        if (!TextUtils.isEmpty(tips)) builder.setTicker(tips);
        String title = intent.getStringExtra(TITLE);
        if (!TextUtils.isEmpty(title)) builder.setContentTitle(title);
        int sIcon = intent.getIntExtra(S_ICON, 0);
        if (sIcon != 0) builder.setSmallIcon(sIcon);
        int lIcon = intent.getIntExtra(L_ICON, 0);
        if (lIcon != 0) {
            builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), lIcon));
        }
        builder.setContentText(String.format("%s0%%", downloadTips));
        Notification notification = builder.build();
        startForeground(ID, notification);
    }


}
