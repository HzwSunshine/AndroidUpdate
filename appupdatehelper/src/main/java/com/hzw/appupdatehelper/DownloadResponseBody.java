package com.hzw.appupdatehelper;

import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.Okio;

/**
 * 功能：
 * Created by 何志伟 on 2017/7/27.
 */

class DownloadResponseBody extends ResponseBody {

    private UpdateListener callBack;
    private BufferedSource source;
    private ResponseBody body;

    DownloadResponseBody(ResponseBody body, UpdateListener callBack) {
        this.callBack = callBack;
        this.body = body;
    }

    @Override
    public MediaType contentType() {
        return body.contentType();
    }

    @Override
    public long contentLength() {
        return body.contentLength();
    }

    @Override
    public BufferedSource source() {
        if (callBack == null) return body.source();
        DownloadStream stream = new DownloadStream(body.source().inputStream(), callBack, contentLength());
        source = Okio.buffer(Okio.source(stream));
        return source;
    }

    @Override
    public void close() {
        super.close();
        if (source != null) {
            try {
                source.close();
            } catch (IOException ignored) {
            }
        }
    }
}
