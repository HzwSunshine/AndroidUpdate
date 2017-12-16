package com.hzw.appupdatehelper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 功能：app更新辅助工具类
 * Created by 何志伟 on 2017/11/2.
 */
class AppUpdateUtil {

    private static final String APP_UPDATE_CACHE = "AppUpdateCache";

    private static boolean isContinueInstall(Context context, String apkFilePath) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            PackageInfo apkInfo =
                    pm.getPackageArchiveInfo(apkFilePath, PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES);
            if (info.packageName.equals(apkInfo.packageName) && apkInfo.versionCode > info.versionCode) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    static boolean isValidPath(String path) {
        String state = Environment.getExternalStorageState();
        if (state.equals(Environment.MEDIA_MOUNTED)) {
            String root = Environment.getExternalStorageDirectory()
                    .getPath();
            if (path.contains(root) && path.endsWith(".apk")) return true;
        }
        return false;
    }

    static void clearApkSize(Context context) {
        SharedPreferences pf = context.getSharedPreferences(APP_UPDATE_CACHE, Context.MODE_PRIVATE);
        pf.edit()
                .clear()
                .apply();
    }

    static long getApkSize(Context context, String path) {
        SharedPreferences pf = context.getSharedPreferences(APP_UPDATE_CACHE, Context.MODE_PRIVATE);
        return pf.getLong(path, 0);
    }

    static void setApkSize(Context context, String path, long apkSize) {
        SharedPreferences pf = context.getSharedPreferences(APP_UPDATE_CACHE, Context.MODE_PRIVATE);
        pf.edit()
                .putLong(path, apkSize)
                .apply();
    }

    static String getApkName(String url) {
        String regEx = "[:&=./?]";
        Matcher m = Pattern.compile(regEx)
                .matcher(url);
        return m.replaceAll("")
                .trim();
    }

    static void installApk(Context context, String apkPath) {
        //if (!isContinueInstall(context, apkPath)) return;
        if (TextUtils.isEmpty(apkPath) || context == null) return;
        File apk = new File(apkPath);
        if (!apk.exists()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final String type = "application/vnd.android.package-archive";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {//Android 7.0安装适配
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String authority = context.getPackageName() + ".fileProvider";
            Uri contentUri = FileProvider.getUriForFile(context, authority, apk);
            intent.setDataAndType(contentUri, type);
        } else {
            intent.setDataAndType(Uri.fromFile(apk), type);
        }
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        }
    }

    static long getAvailableStorage() {
        try {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return availableBlocks * blockSize;
        } catch (Exception e) {
            return 0;
        }
    }

    static boolean isNetAvailable(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            NetworkInfo info = manager.getActiveNetworkInfo();
            return info != null && info.isAvailable();
        } catch (Exception ignored) {
        }
        return false;
    }

}
