package com.hzw.appupdatehelper;

import android.content.Context;
import android.text.TextUtils;


/**
 * 功能：更新参数配置
 * Created by 何志伟 on 2017/11/2.
 */
public class UpdateConfig {

    boolean breakPointDownload = true;
    UpdateListener updateListener;
    private Context context;
    boolean isNotify = true;
    String downloadTips;
    String startTips;
    String filePath;
    int largeIcon;
    int smallIcon;
    int iconColor;
    String title;
    String url;

    private UpdateConfig() {
    }

    UpdateConfig(Context context) {
        this.context = context;
    }

    public UpdateConfig url(String url) {
        this.url = url;
        return this;
    }

    public UpdateConfig title(String title) {
        this.title = title;
        return this;
    }

    public UpdateConfig downloadTips(String downloadTips) {
        this.downloadTips = downloadTips;
        return this;
    }

    public UpdateConfig isNotify(boolean isNotify) {
        this.isNotify = isNotify;
        return this;
    }

    public UpdateConfig breakPointDownload(boolean breakPointDownload) {
        this.breakPointDownload = breakPointDownload;
        return this;
    }

    public UpdateConfig filePath(String filePath) {
        this.filePath = filePath;
        return this;
    }

    public UpdateConfig startTips(String startTips) {
        this.startTips = startTips;
        return this;
    }

    public UpdateConfig largeIcon(int largeIcon) {
        this.largeIcon = largeIcon;
        return this;
    }

    public UpdateConfig smallIcon(int smallIcon) {
        this.smallIcon = smallIcon;
        return this;
    }

    public UpdateConfig iconColor(int iconColor) {
        this.iconColor = iconColor;
        return this;
    }

    public UpdateConfig setUpdateListener(UpdateListener listener) {
        this.updateListener = listener;
        return this;
    }

    public void update() {
        if (context == null) {
            throw new NullPointerException("context is null，AppUpdateHelper need an Context!");
        }
        if (TextUtils.isEmpty(url)) {
            throw new IllegalArgumentException("the download url can not be empty!");
        }
        AppUpdateHelper.getInstance().update(context, this);
    }


}
