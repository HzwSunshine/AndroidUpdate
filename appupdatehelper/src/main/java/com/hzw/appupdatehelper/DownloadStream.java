package com.hzw.appupdatehelper;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * 功能：
 * Created by 何志伟 on 2017/7/27.
 */

class DownloadStream extends InputStream {

    private UpdateListener callBack;
    private InputStream stream;
    private long totalRead;
    private long total;

    DownloadStream(InputStream stream, UpdateListener callBack, long total) {
        this.callBack = callBack;
        this.stream = stream;
        this.total = total;
    }

    @Override
    public int read() throws IOException {
        int read = stream.read();
        if (total < 0) {
            callBack.progress(-1, -1, -1);
            return read;
        }
        if (read >= 0) {
            totalRead++;
            callBack.progress(totalRead, total, totalRead * 1.0f / total);
        }
        return read;
    }

    @Override
    public int read(@NonNull byte[] b, int off, int len) throws IOException {
        int read = stream.read(b, off, len);
        if (total < 0) {
            callBack.progress(-1, -1, -1);
            return read;
        }
        if (read >= 0) {
            totalRead += read;
            callBack.progress(totalRead, total, totalRead * 1.0f / total);
        }
        return read;
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (stream != null) stream.close();
    }


}
