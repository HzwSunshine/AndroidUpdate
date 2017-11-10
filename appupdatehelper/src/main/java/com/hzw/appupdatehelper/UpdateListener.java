package com.hzw.appupdatehelper;


/**
 * 功能：
 * Created by 何志伟 on 2017/11/2.
 */
public abstract class UpdateListener {

    public abstract void progress(long numByte, long totalBytes, float per);

    public void threadProgress(long numByte, long totalBytes, float per) {
    }

    public abstract void error(int errorType);

}
