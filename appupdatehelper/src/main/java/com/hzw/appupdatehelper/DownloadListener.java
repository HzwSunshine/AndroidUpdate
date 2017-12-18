package com.hzw.appupdatehelper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

/**
 * 功能：下载监听
 * Created by 何志伟 on 2017/7/27.
 */
abstract class DownloadListener implements Callback {

    private Handler handler = new Handler(Looper.getMainLooper());
    private long numBytes, totalBytes;
    private boolean isPointDownload;
    private long apkInitLength;
    private String filePath;
    private Context context;
    private float per;

    DownloadListener(String filePath, Context context, boolean isPointDownload) {
        this.isPointDownload = isPointDownload;
        this.filePath = filePath;
        this.context = context;
    }

    @Override public void onFailure(Call call, IOException e) {
        error(e, 0);
    }

    @Override public void onResponse(Call call, Response response) throws IOException {
        if (response.isSuccessful()) {
            ResponseBody body = new DownloadResponseBody(response.body(), new UpdateListener() {
                @Override public void progress(long numByte, long totalBytes, float per) {
                    numByte += apkInitLength;
                    totalBytes += apkInitLength;
                    per = numByte * 1.0f / totalBytes;
                    DownloadListener.this.totalBytes = totalBytes;
                    DownloadListener.this.numBytes = numByte;
                    DownloadListener.this.per = per;
                    DownloadListener.this.threadProgress(numByte, totalBytes, per);
                    if (handler != null) handler.post(runnable);
                }

                @Override public void error(int errorType) {
                }
            });
            BufferedSource source = body.source();
            File file = new File(filePath);
            //如果不支持断点下载，删除已下载文件
            if (!isPointDownload) {
                file.delete();
            }
            if (!file.exists()) {
                file.getParentFile()
                        .mkdirs();
                file.createNewFile();
            }
            apkInitLength = file.length();
            long totalSize = body.contentLength() + apkInitLength;
            AppUpdateUtil.setApkSize(context, filePath, totalSize);
            if (AppUpdateUtil.getAvailableStorage() < totalSize) {
                //存储空间不足
                error(null, AppUpdateHelper.ERROR_STORAGE_LACK);
                return;
            }
            try {
                BufferedSink sink = Okio.buffer(Okio.appendingSink(file));
                source.readAll(sink);
                sink.flush();
                source.close();
            } catch (IOException e) {
                error(e, 0);
            }
        } else {
            error(null, response.code());
        }
    }

    private Runnable runnable = new Runnable() {
        @Override public void run() {
            progress(numBytes, totalBytes, per);
            if (numBytes == totalBytes) {
                release();
            }
        }
    };

    /**
     * 释放下载资源
     */
    private void release() {
        if (handler != null) {
            handler.removeCallbacks(null);
            runnable = null;
            handler = null;
        }
    }

    public void threadProgress(long currentBytes, long totalBytes, float per) {
    }

    private void error(Exception e, int errorCode) {
        int errorType = 0;
        if (e == null) {
            errorType = errorCode;
        } else {
            String errorName = e.getClass()
                    .getName();
            if (ConnectException.class.getName()
                    .equals(errorName)) {//网络未连接
                errorType = AppUpdateHelper.ERROR_NO_NETWORK;
            } else if (SocketTimeoutException.class.getName()
                    .equals(errorName)) {//网络连接超时
                errorType = AppUpdateHelper.ERROR_CONN_TIMEOUT;
            } else if (SocketException.class.getName()
                    .equals(errorName)) {//网络连接关闭
                errorType = AppUpdateHelper.ERROR_NO_NETWORK;
            } else if (FileNotFoundException.class.getName()
                    .equals(errorName)) {//文件未找到
                errorType = AppUpdateHelper.ERROR_PATH_NOT_VALID;
            }
        }
        final int errorTypeCopy = errorType;
        handler.post(new Runnable() {
            @Override public void run() {
                error(errorTypeCopy);
            }
        });
        //不需要断点下载，失败时，删除已下载文件
        if (!isPointDownload) {
            new File(filePath).delete();
        }
        release();
    }

    //下载回调
    public abstract void progress(long currentBytes, long totalBytes, float per);

    public abstract void error(int errorType);

}
