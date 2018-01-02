package com.hzw.appupdate;

import android.Manifest;
import android.content.Intent;
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


public class MainActivity extends AppCompatActivity {

    String url = "http://imtt.dd.qq.com/16891/F20F0CB123B2C01698175719B03BAB75.apk?fsname=com.android36kr.app_6.5_17121215.apk&csr=1bbd";
    private ProgressBar progressBar;
    private TextView show;
    private String path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressBar = findViewById(R.id.progressBar);
        show = findViewById(R.id.progressShow);
        findViewById(R.id.download).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPermission();
            }
        });

        //如果服务器支持断点续传，那么你可能需要，当前已经断点下载的文件百分比。
        float per = AppUpdateHelper.getInstance().getDownloadProgress(this, url);
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
        }else{
            Toast.makeText(getApplication(), "权限都不给？回家放羊去吧！", Toast.LENGTH_SHORT).show();
        }
    }

    private void download() {
        AppUpdateHelper.with(this)
                .url(url)
                .filePath(null)//自定义的文件路径
                .title("app的更新")//通知标题
                .startTips("开始下载啦")//通知提示
                .downloadTips("下载中")//通知text
                .iconColor(Color.RED)//通知小图标颜色
                .largeIcon(R.mipmap.ic_launcher)//通知小图标
                .smallIcon(R.mipmap.ic_launcher)//通知大图标
                .isNotify(true)//是否显示通知，默认为显示，如果不需要显示，那么也就不需要上述通知设置
                .breakPointDownload(true)//是否需要断点下载，默认为true，如果服务器不支持可以关掉，所以请先确认下你们服务器是否支持断点下载
                .setUpdateListener(new UpdateListener() {
                    //下载进度监听，需要需要在异步线程中做点事，
                    // 可以重写threadProgress(long numByte, long totalBytes, float per)方法
                    @Override
                    public void progress(long numByte, long totalBytes, float per) {
                        progressBar.setProgress((int) (per * 100));
                        show.setText(String.format("%s%%", (int) (per * 100)));
                    }

                    @Override
                    public void threadProgress(long numByte, long totalBytes, float per) {
                        super.threadProgress(numByte, totalBytes, per);
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
                }).update();//开始更新
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //当进度条显示界面销毁的时候，不需要再展示进度时，记得调用下此方法，其实就是取消进度，防止内存泄漏
        //activity或fragment中可以在onDestroy（）方法中调用
        //dialog中可以在dialog消失时调用下
        AppUpdateHelper.release();
    }

}
