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

    String url = "http://imtt.dd.qq.com/16891/F20F0CB123B2C01698175719B03BAB75.apk?fsname=com.android36kr.app_6.5_17121215.apk&csr=1bbd";
    private ProgressBar progressBar;
    private TextView show;
    private String path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        progressBar = findViewById(R.id.progressBar);
        show = findViewById(R.id.progressShow);

        path = getExternalFilesDir("").getPath() + "/xdwwr133.apk";

        findViewById(R.id.download).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPermission();
            }
        });

        float per = AppUpdateHelper.getInstance().getDownloadProgress(this, path);
        progressBar.setProgress((int) (per * 100));
        show.setText(String.valueOf((int) (per * 100)));
        if (per > 0 && per < 1) {
            checkPermission();
        }
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
                .filePath(path)
                .startTips("开始下载啦")
                .largeIcon(R.mipmap.ic_launcher)
                .smallIcon(R.mipmap.ic_launcher)
                .title("app的更新")
                .iconColor(Color.RED)
                .breakPointDownload(true)
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
                            case AppUpdateHelper.ERROR_STORAGE_LACK:
                                Toast.makeText(getApplicationContext(), "存储空间不够", Toast.LENGTH_SHORT)
                                        .show();
                                break;
                        }
                    }
                }).update();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppUpdateHelper.release();
    }


}

