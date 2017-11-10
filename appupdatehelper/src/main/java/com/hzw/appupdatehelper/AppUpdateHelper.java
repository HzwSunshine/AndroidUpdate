package com.hzw.appupdatehelper;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Environment;
import android.os.IBinder;
import android.text.TextUtils;

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

    public static final int ERROR_NO_NETWORK = 0;
    public static final int ERROR_CONN_TIMEOUT = 1;
    public static final int ERROR_PATH_NOT_VALID = 2;
    static final int NO_POINT_FLAG = -1;
    private static volatile AppUpdateHelper instance;
    private ServiceConnection connection;
    private static OkHttpClient client;
    private ApkReceiver receiver;
    private UpdateConfig config;

    private AppUpdateHelper() {
        client = new OkHttpClient().newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.MINUTES)
                .readTimeout(1, TimeUnit.MINUTES)
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

    protected void update(Context context, UpdateConfig config) {
        //当前下载任务执行时，不在重复执行
        if (isExecute(config.url) && connection != null) return;
        //注册app替换更新广播
        initReceiver(context);
        this.config = config;
        File apk;
        if (TextUtils.isEmpty(config.filePath)) {
            String fileName = AppUpdateUtil.getApkName(config.url);
            File file = context.getExternalCacheDir();
            apk = new File(file, String.format("%s.apk", fileName));
            config.filePath = apk.getPath();
        } else {
            apk = new File(config.filePath);
            if (!isValidPath(apk.getPath())) {
                if (config.updateListener != null)
                    config.updateListener.error(ERROR_PATH_NOT_VALID);
                return;
            }
        }
        long apkSize = AppUpdateUtil.initFileSize(context, config.filePath, 0);
        if (apk.exists() && apk.length() == apkSize) {
            //apk存在并且完整，直接执行安装
            AppUpdateUtil.installApk(context, apk.getPath());
        } else {
            long start = 0;
            //文件存在，并要求断点下载时
            if (apk.exists()) start = apk.length();
            if (!config.breakPointDownload) start = NO_POINT_FLAG;
            startService(context, start);
        }
    }

    private void startService(Context context, long start) {
        if (connection == null) {
            connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    AppUpdateService.UpdateBinder binder = (AppUpdateService.UpdateBinder) iBinder;
                    AppUpdateService service = binder.getService();
                    service.setUpdateProgress(new UpdateListener() {
                        @Override
                        public void progress(long numByte, long totalBytes, float per) {
                            config.updateListener.progress(numByte, totalBytes, per);
                        }

                        @Override
                        public void error(int errorType) {
                            config.updateListener.error(errorType);
                        }
                    });
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                }
            };
        }
        Intent intent = new Intent(context, AppUpdateService.class);
        intent.putExtra(AppUpdateService.URL, config.url);
        intent.putExtra(AppUpdateService.TITLE, config.title);
        intent.putExtra(AppUpdateService.TIPS, config.startTips);
        intent.putExtra(AppUpdateService.L_ICON, config.largeIcon);
        intent.putExtra(AppUpdateService.S_ICON, config.smallIcon);
        intent.putExtra(AppUpdateService.FILE_PATH, config.filePath);
        intent.putExtra(AppUpdateService.IS_NOTIFY, config.isNotify);
        intent.putExtra(AppUpdateService.DN_TIPS, config.downloadTips);
        intent.putExtra(AppUpdateService.ICON_COLOR, config.iconColor);
        intent.putExtra(AppUpdateService.START, start);
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        context.startService(intent);
    }

    private void initReceiver(Context context) {
        if (receiver != null) return;
        receiver = new ApkReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        context.registerReceiver(receiver, filter);
    }

    private class ApkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())
                    && intent.getDataString() != null) {
                String packageName = intent.getDataString().substring(8);
                //当前apk替换安装完成，释放资源，初始化缓存数据
                if (context.getPackageName().equals(packageName)) {
                    //清除apk大小记录
                    AppUpdateUtil.initFileSize(context, null, 0);
                    //删除已安装的安装包
                    File file = new File(config.filePath);
                    if (file.exists()) file.delete();
                    //释放单例
                    release();
                    //注销广播
                    context.unregisterReceiver(this);
                    receiver = null;
                }
            }
        }
    }

    private boolean isValidPath(String path) {
        String state = Environment.getExternalStorageState();
        if (state.equals(Environment.MEDIA_MOUNTED)) {
            String root = Environment.getExternalStorageDirectory().getPath();
            if (path.contains(root) && path.endsWith(".apk")) return true;
        }
        return false;
    }

    void downloadApk(String url, long start, DownloadListener listener) {
        if (isExecute(url)) return;
        Request.Builder builder = new Request.Builder().url(url).tag(url);
        if (config.breakPointDownload) builder.addHeader("RANGE", "bytes=" + start + "-");
        Call call = getInstance().getClient().newCall(builder.build());
        call.enqueue(listener);
    }

    boolean isExecute(String url) {
        OkHttpClient client = getInstance().getClient();
        for (Call call : client.dispatcher().runningCalls()) {
            if (url.equals(call.request().tag())) return true;
        }
        return false;
    }

    /**
     * 启动该Service的界面销毁时，解除绑定，释放资源，但该Service并不会停止
     */
    public void release(Context context) {
        if (connection != null) {
            context.getApplicationContext().unbindService(connection);
            connection = null;
        }
    }

    private void release() {
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
            float per = file.length() * 1f / AppUpdateUtil.initFileSize(context, file.getPath(), 0);
            return per > 1 ? 0 : per;
        } else {
            File file = new File(urlOrPath);
            float per = file.length() * 1f / AppUpdateUtil.initFileSize(context, urlOrPath, 0);
            return per > 1 ? 0 : per;
        }
    }

//    /**
//     * 取消下载，仅测试使用。。。
//     */
//    public void cancelUpdate(Context context) {
//        connection = null;
//        release(context);
//        if (currentCall != null && currentCall.isExecuted()) {
//            currentCall.cancel();
//            currentCall = null;
//        }
//    }


}
