package com.hzw.appupdate;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.hzw.appupdatehelper.AppUpdateHelper;
import com.hzw.appupdatehelper.UpdateListener;

public class TestActivity extends AppCompatActivity {

    String url = "http://imtt.dd.qq.com/16891/7259765A7FAE6159A0338A6339CCCB29.apk?fsname=com.hrhb.bdt_3.3.0_15.apk&csr=1bbd";
    private ProgressBar progressBar;
    private TextView show;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        progressBar = findViewById(R.id.progressBar);
        show = findViewById(R.id.progressShow);

        findViewById(R.id.download).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplication(), "开始下载", Toast.LENGTH_SHORT).show();
                checkPermission();
            }
        });

        float per = AppUpdateHelper.getInstance().getDownloadProgress(this, url);
        progressBar.setProgress((int) (per * 100));
        show.setText(String.valueOf((int) (per * 100)));
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        } else {
            download();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 1) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            download();
        }
    }

    private void download() {
        AppUpdateHelper.with(this)
                .url(url)
                .filePath(null)
                .startTips("开始下载啦")
                .largeIcon(R.mipmap.ic_launcher)
                .smallIcon(R.mipmap.ic_launcher)
                .title("app的更新")
                .iconColor(Color.RED)
                .breakPointDownload(false)
                .downloadTips("下载中")
                .isNotify(true)
                .setUpdateListener(new UpdateListener() {
                    @Override
                    public void progress(long numByte, long totalBytes, float per) {
                        progressBar.setProgress((int) (per * 100));
                        show.setText(String.format("%s%%",(int) (per * 100)));
                    }

                    @Override
                    public void error(int errorType) {
                        switch (errorType) {
                            case AppUpdateHelper.ERROR_NO_NETWORK:
                                Toast.makeText(getApplication(), "网络连接失败", Toast.LENGTH_SHORT).show();
                                break;
                            case AppUpdateHelper.ERROR_CONN_TIMEOUT:
                                Toast.makeText(getApplication(), "网络连接超时", Toast.LENGTH_SHORT).show();
                                break;
                            case AppUpdateHelper.ERROR_PATH_NOT_VALID:
                                Toast.makeText(getApplication(), "配置的路径不可用", Toast.LENGTH_SHORT).show();
                                break;
                        }
                    }
                }).update();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppUpdateHelper.getInstance().release(this);
    }


}

