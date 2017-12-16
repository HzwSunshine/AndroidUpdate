package com.hzw.appupdatehelper;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import java.io.File;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * 功能：基于okhttp的apk下载更新
 * Created by 何志伟 on 2017/11/2.
 */
public class AppUpdateHelper {

    //无网络
    public static final int ERROR_NO_NETWORK = -1;
    //网络超时
    public static final int ERROR_CONN_TIMEOUT = 1;
    //下载路劲不可用
    public static final int ERROR_PATH_NOT_VALID = 2;
    //存储空间不足
    public static final int ERROR_STORAGE_LACK = 3;
    //无断点标识
    private static final int NO_POINT_FLAG = -1;

    private static volatile AppUpdateHelper instance;
    private NotificationCompat.Builder notifyBuilder;
    private NotificationManager manager;
    private static OkHttpClient client;
    static final int NOTIFY_ID = 1314;
    private UpdateConfig config;
    private ApkReceiver receiver;

    private AppUpdateHelper() {
        client = new OkHttpClient().newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.MINUTES)
                .readTimeout(1, TimeUnit.MINUTES)
                .retryOnConnectionFailure(false)
                .build();
    }

    public static AppUpdateHelper getInstance() {
        if (instance == null) {
            synchronized (AppUpdateHelper.class) {
                if (instance == null) {
                    instance = new AppUpdateHelper();
                }
            }
        }
        return instance;
    }

    private OkHttpClient getClient() {
        return client;
    }

    public static UpdateConfig with(Context context) {
        return new UpdateConfig(context.getApplicationContext());
    }

    void update(Context context, UpdateConfig config) {
        this.config = config;
        //当前下载任务执行时，不在重复执行
        if (isExecute(config.url)) return;
        File apk;
        if (TextUtils.isEmpty(config.filePath)) {
            String fileName = AppUpdateUtil.getApkName(config.url);
            File file = context.getExternalCacheDir();
            apk = new File(file, String.format("%s.apk", fileName));
            config.filePath = apk.getPath();
        } else {
            apk = new File(config.filePath);
            if (!AppUpdateUtil.isValidPath(apk.getPath())) {//判断配置的路径是否可用
                releaseHelper(context);
                if (config.updateListener != null) {
                    config.updateListener.error(ERROR_PATH_NOT_VALID);
                }
                return;
            }
        }

        long apkSize = AppUpdateUtil.getApkSize(context, config.filePath);
        if (apk.exists() && apk.length() == apkSize) {
            //注册app替换更新广播
            initReceiver(context);
            //apk存在并且完整，直接执行安装
            AppUpdateUtil.installApk(context, apk.getPath());
        } else if (AppUpdateUtil.getAvailableStorage() < apkSize) {
            releaseHelper(context);
            //存储空间不足
            if (config.updateListener != null) {
                config.updateListener.error(ERROR_STORAGE_LACK);
            }
        } else {
            //网络检查
            if (!AppUpdateUtil.isNetAvailable(context) && config.updateListener != null) {
                config.updateListener.error(ERROR_NO_NETWORK);
                //无网络错误时，不释放单例
                return;
            }
            long start = 0;
            //文件存在，并要求断点下载时
            if (apk.exists()) {
                start = apk.length();
            }
            if (!config.breakPointDownload) {
                start = NO_POINT_FLAG;
            }
            //注册app替换更新广播
            initReceiver(context);
            //开始下载
            startService(context, start);
        }
    }

    private void startService(Context context, long start) {
        Intent intent = new Intent(context, AppUpdateService.class);
        intent.putExtra(AppUpdateService.IS_NOTIFY, config.isNotify);
        intent.putExtra(AppUpdateService.START, start);
        context.startService(intent);
    }

    private void stopDownloadService(Context context) {
        cancelNotify();
        Intent intent = new Intent(context, AppUpdateService.class);
        context.stopService(intent);
    }

    private void initReceiver(Context context) {
        if (receiver != null) return;
        receiver = new ApkReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MY_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        context.registerReceiver(receiver, filter);
    }

    private class ApkReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent != null && Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
                //当前apk替换安装完成，释放资源，初始化缓存数据
                //清除apk大小记录
                AppUpdateUtil.clearApkSize(context);
                //删除已安装的安装包
                File file = new File(config.filePath);
                if (file.exists()) file.delete();
                //释放单例
                releaseHelper(context);
                receiver = null;
            }
        }
    }

    void downloadApk(final Context context, long start) {
        if (isExecute(config.url)) return;
        final Request.Builder builder = new Request.Builder().url(config.url)
                .tag(config.url);
        if (config.breakPointDownload) {
            builder.addHeader("RANGE", "bytes=" + start + "-");
        }
        Call call = getInstance().getClient()
                .newCall(builder.build());
        boolean isPointDownload = start != AppUpdateHelper.NO_POINT_FLAG;
        call.enqueue(new DownloadListener(config.filePath, context, isPointDownload) {
            @Override public void progress(long currentBytes, long totalBytes, float per) {
                if (config.updateListener != null) {
                    config.updateListener.progress(currentBytes, totalBytes, per);
                    Log.i("xxx", "progress: " + per);
                }
                if (currentBytes == totalBytes) {
                    //下载完成，安装apk，释放资源
                    AppUpdateUtil.installApk(context, config.filePath);
                    //释放service资源
                    stopDownloadService(context);
                }
            }

            @Override public void threadProgress(long currentBytes, long totalBytes, float per) {
                if (config.updateListener != null) {
                    config.updateListener.threadProgress(currentBytes, totalBytes, per);
                }
                if (config.isNotify && getNotifyBuilder(context) != null) {
                    int len = (int) (per * 100);
                    notifyBuilder.setProgress(100, len, false);
                    notifyBuilder.setContentText(String.format("%s%s%%", config.downloadTips, len));
                    manager.notify(NOTIFY_ID, notifyBuilder.build());
                }
            }

            @Override public void error(int errorType) {
                if (config.updateListener != null) {
                    config.updateListener.error(errorType);
                }
                //释放helper资源
                releaseHelper(context);
                //下载失败时，停止服务
                stopDownloadService(context);
            }
        });
    }


    NotificationCompat.Builder getNotifyBuilder(Context context) {
        if (!config.isNotify) return null;
        if (manager == null) {
            manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notifyBuilder = new NotificationCompat.Builder(context, "AppUpdate");
            notifyBuilder.setWhen(System.currentTimeMillis());
            notifyBuilder.setAutoCancel(false);//禁止用户点击删除按钮删除
            notifyBuilder.setOngoing(true);//禁止滑动删除
            notifyBuilder.setShowWhen(true);//右上角的时间显示
            if (config.iconColor != 0) {
                notifyBuilder.setColor(config.iconColor);
            }
            if (!TextUtils.isEmpty(config.startTips)) {
                notifyBuilder.setTicker(config.startTips);
            }
            if (!TextUtils.isEmpty(config.title)) {
                notifyBuilder.setContentTitle(config.title);
            }
            if (config.smallIcon != 0) {
                notifyBuilder.setSmallIcon(config.smallIcon);
            }
            if (config.largeIcon != 0) {
                notifyBuilder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), config.largeIcon));
            }
            notifyBuilder.setContentText(String.format("%s0%%", config.downloadTips));
        }
        return notifyBuilder;
    }

    private void cancelNotify() {
        if (manager != null) {
            manager.cancel(NOTIFY_ID);
            notifyBuilder = null;
            manager = null;
        }
    }

    private boolean isExecute(String url) {
        OkHttpClient client = getInstance().getClient();
        for (Call call : client.dispatcher()
                .runningCalls()) {
            if (url.equals(call.request()
                                   .tag())) {
                return true;
            }
        }
        return false;
    }

    private void releaseHelper(Context context) {
        //清除已注册的广播
        if (receiver != null) {
            context.unregisterReceiver(receiver);
            receiver = null;
        }
        config = null;
        client = null;
        instance = null;
    }

    public float getDownloadProgress(Context context, String urlOrPath) {
        if (TextUtils.isEmpty(urlOrPath)) return 0;
        if (urlOrPath.startsWith("http")) {
            String fileName = AppUpdateUtil.getApkName(urlOrPath);
            File file = context.getExternalCacheDir();
            file = new File(file, String.format("%s.apk", fileName));
            float per = file.length() * 1f / AppUpdateUtil.getApkSize(context, file.getPath());
            return per > 1 ? 0 : per;
        } else {
            File file = new File(urlOrPath);
            float per = file.length() * 1f / AppUpdateUtil.getApkSize(context, urlOrPath);
            return per > 1 ? 0 : per;
        }
    }

    /**
     * 进度条界面退出时，取消进度回调
     */
    public static void release() {
        if (instance == null) return;
        UpdateConfig config = getInstance().config;
        if (config != null) config.updateListener = null;
    }

    //    /**
    //     * 取消下载，仅测试使用。。。
    //     */
    //    public void cancelUpdate(Context context) {
    //        connection = null;
    //        releaseConn(context);
    //        if (currentCall != null && currentCall.isExecuted()) {
    //            currentCall.cancel();
    //            currentCall = null;
    //        }
    //    }

}
