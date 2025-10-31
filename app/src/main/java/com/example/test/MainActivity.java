package com.example.test;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
//import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.Objects;

import com.example.test.ForegroundService;
import com.example.test.LocalService;
import com.example.test.RemoteService;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.util.Log;
import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private final int PICK_REQUEST = 10001;
    ValueCallback<Uri> mFilePathCallback;
    ValueCallback<Uri[]> mFilePathCallbackArray;
    private static final int JOB_ID = 100;
    private String startUrl = null;
    private String pageTitle = "";
    
    // 全屏视频播放相关字段
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout fullscreenContainer;
    // 广告拦截开关与域名列表
    private boolean enableAdBlock = false;
    private static final Set<String> AD_HOSTS = new HashSet<>(Arrays.asList(
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "googleadservices.com",
        "pagead2.googlesyndication.com",
        "securepubads.g.doubleclick.net",
        "adservice.google.com",
        "adservice.google.cn",
        "ads.yahoo.com",
        "ads-twitter.com",
        "adserver",
        "admob.com",
        "facebook.com/tr",
        "advertising",
        "adservice",
        "googlesyndication.com"
    ));

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //隐藏ActionBar
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_main);
        //WebView加载页面
        webView = findViewById(R.id.web_view);
        webView.getSettings().setJavaScriptEnabled(true);

        // 启用并持久化 Cookie，包括第三方 Cookie（API 21+）
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
        // code from https://blog.csdn.net/qq_21138819/article/details/56676007 by 欢子-3824
        webView.setWebChromeClient(new WebChromeClient() {
            // Andorid 4.1----4.4
            public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType, String capture) {

                mFilePathCallback = uploadFile;
                handle(uploadFile);
            }

            // for 5.0+
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mFilePathCallbackArray != null) {
                    mFilePathCallbackArray.onReceiveValue(null);
                }
                mFilePathCallbackArray = filePathCallback;
                handleup(filePathCallback);
                return true;
            }

            private void handle(ValueCallback<Uri> uploadFile) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                // 设置允许上传的文件类型
                intent.setType("*/*");
                startActivityForResult(intent, PICK_REQUEST);
            }

            private void handleup(ValueCallback<Uri[]> uploadFile) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("*/*");
                startActivityForResult(intent, PICK_REQUEST);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                pageTitle = title != null ? title : "";
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                super.onReceivedIcon(view, icon);
                if (icon != null) {
                    updateTaskIcon(icon, pageTitle);
                }
            }

            // 全屏视频播放支持
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                
                customView = view;
                customViewCallback = callback;
                
                // 创建全屏容器
                if (fullscreenContainer == null) {
                    fullscreenContainer = new FrameLayout(MainActivity.this);
                    fullscreenContainer.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                    fullscreenContainer.setBackgroundColor(Color.BLACK);
                }
                
                // 隐藏 WebView
                webView.setVisibility(View.GONE);
                
                // 添加自定义视图到全屏容器
                fullscreenContainer.addView(customView);
                
                // 将全屏容器添加到根布局
                ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
                rootView.addView(fullscreenContainer);
                
                // 隐藏状态栏和导航栏
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) {
                    return;
                }
                
                // 显示 WebView
                webView.setVisibility(View.VISIBLE);
                
                // 移除全屏容器
                ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
                if (fullscreenContainer != null) {
                    rootView.removeView(fullscreenContainer);
                    fullscreenContainer.removeView(customView);
                }
                
                // 恢复系统UI
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                
                // 清理
                customView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
            }
        });

        // wevView监听 H5 页面的下载事件
        // code from https://github.com/madhan98/Android-webview-upload-download/blob/master/app/src/main/java/com/my/newproject/MainActivity.java by Madhan
        webView.setDownloadListener(new DownloadListener() {

            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                String cookies = CookieManager.getInstance().getCookie(url);

                request.addRequestHeader("cookie", cookies);

                request.addRequestHeader("User-Agent", userAgent);

                request.setDescription("下载中...");

                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));

                request.allowScanningByMediaScanner(); request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));

                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

                manager.enqueue(request);

                showMessage("下载中...");

                //Notif if success

                BroadcastReceiver onComplete = new BroadcastReceiver() {

                    public void onReceive(Context ctxt, Intent intent) {

                        showMessage("下载完成");

                        unregisterReceiver(this);

                    }};

                registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

            }

        });

        // 使用自定义 WebViewClient：拦截跳转并在页面加载完成时恢复 sessionStorage
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            // API >= 21 的拦截
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    if (!enableAdBlock || request == null) return super.shouldInterceptRequest(view, request);
                    Uri uri = request.getUrl();
                    if (isAdUrl(uri)) {
                        return emptyResponse();
                    }
                } catch (Throwable t) {
                    // 忽略错误，继续默认加载
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 从 localStorage 恢复 sessionStorage（跨进程/重启保持登录状态）
                // 仅恢复以特定前缀保存的键，避免干扰站点自身的 localStorage
                String jsRestore = "(function(){try{var P='__WEBVIEW_SESSION__';for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(k&&k.indexOf(P)===0){var kk=k.substring(P.length);var v=localStorage.getItem(k);sessionStorage.setItem(kk,v);}}}catch(e){}})()";
                view.loadUrl("javascript:" + jsRestore);
            }
        });

        // 这里填你需要打包的 H5 页面链接，支持从 Intent 动态指定
        startUrl = getIntent() != null ? getIntent().getStringExtra("START_URL") : null;
        if (startUrl == null || startUrl.isEmpty()) {
            startUrl = "https://zh.xhamster.com/";
        }
        webView.loadUrl(startUrl);

        //显示一些小图片（头像）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        // 允许使用 localStorage / sessionStorage，并启用数据库存储
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        // 是否支持 html 的 meta 标签
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        // startForegroundService();

        webView.loadUrl("javascript:(function() { " +
            "var audio = document.getElementById('alarmSound');" +
            "audio.load();" +
            "})()");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            // 将 sessionStorage 备份到 localStorage（带前缀），实现会话跨进程/重启恢复
            String jsBackup = "(function(){try{var P='__WEBVIEW_SESSION__';for(var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i);var v=sessionStorage.getItem(k);localStorage.setItem(P + k, v);}}catch(e){}})()";
            webView.loadUrl("javascript:" + jsBackup);
        }
    }

    private void updateTaskIcon(Bitmap icon, String title) {
        try {
            int primaryColor = Color.parseColor("#222222");
            ActivityManager.TaskDescription td = new ActivityManager.TaskDescription(
                    title != null && !title.isEmpty() ? title : getString(R.string.app_name),
                    icon,
                    primaryColor
            );
            setTaskDescription(td);
        } catch (Throwable t) {
            Log.w("MainActivity", "updateTaskIcon failed", t);
        }
    }

    // 简单判断是否为广告 URL：匹配域名与路径关键词
    private boolean isAdUrl(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        String path = uri.getPath();
        String url = uri.toString();
        if (host == null) return false;
        String h = host.toLowerCase();
        // 域名命中（包含或全匹配）
        for (String adHost : AD_HOSTS) {
            if (h.equals(adHost) || h.endsWith("." + adHost) || h.contains(adHost)) {
                return true;
            }
        }
        // 路径/URL 关键词命中
        String p = (path == null ? "" : path.toLowerCase());
        String u = url.toLowerCase();
        return (p.contains("/ads/") || p.contains("/ad/") || p.contains("advert") || p.contains("sponsor")
            || u.contains("/ads?") || u.contains("adservice") || u.contains("pagead") || u.contains("doubleclick"));
    }

    // 返回一个空响应以阻断资源加载
    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

    //设置回退页面
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 如果当前处于全屏视频模式，先退出全屏
            if (customView != null) {
                exitFullscreenIfNeeded();
                return true;
            }
            // 如果 WebView 可以后退，则后退
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // 退出全屏（用于返回键处理）
    private void exitFullscreenIfNeeded() {
        if (customView == null) return;
        // 显示 WebView
        webView.setVisibility(View.VISIBLE);
        // 移除全屏容器
        ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
        if (fullscreenContainer != null) {
            rootView.removeView(fullscreenContainer);
            fullscreenContainer.removeView(customView);
        }
        // 恢复系统UI
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        // 清理
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    @Deprecated
    public void showMessage(String _s) {
        Toast.makeText(getApplicationContext(), _s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webView.destroy();
        webView = null;
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_REQUEST) {
            if (null != data) {
                Uri uri = data.getData();
                handleCallback(uri);
            } else {
                // 取消了照片选取的时候调用
                handleCallback(null);
            }
        } else {
            // 取消了照片选取的时候调用
            handleCallback(null);
        }
    }

    /**
     * 处理WebView的回调
     *
     * @param uri
     */
    private void handleCallback(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mFilePathCallbackArray != null) {
                if (uri != null) {
                    mFilePathCallbackArray.onReceiveValue(new Uri[]{uri});
                } else {
                    mFilePathCallbackArray.onReceiveValue(null);
                }
                mFilePathCallbackArray = null;
            }
        } else {
            if (mFilePathCallback != null) {
                if (uri != null) {
                    String url = getFilePathFromContentUri(uri, getContentResolver());
                    Uri u = Uri.fromFile(new File(url));

                    mFilePathCallback.onReceiveValue(u);
                } else {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = null;
            }
        }
    }

    public static String getFilePathFromContentUri(Uri selectedVideoUri, ContentResolver contentResolver) {
        String filePath;
        String[] filePathColumn = {MediaStore.MediaColumns.DATA};

        Cursor cursor = contentResolver.query(selectedVideoUri, filePathColumn, null, null, null);
//      也可用下面的方法拿到cursor
//      Cursor cursor = this.context.managedQuery(selectedVideoUri, filePathColumn, null, null, null);

        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        filePath = cursor.getString(columnIndex);
        cursor.close();
        return filePath;
    }

    private void startForegroundService() {
        // 启动前台服务
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        startService(serviceIntent);
        
        // 启动双进程保活服务
        startService(new Intent(this, LocalService.class));
        startService(new Intent(this, RemoteService.class));
        
        // 设置并启动 JobScheduler
        scheduleJob();
    }
    
    private void scheduleJob() {
        ComponentName serviceComponent = new ComponentName(this, JobSchedulerService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, serviceComponent);
        
        // 设置任务在网络可用时执行
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        
        // 设置任务在设备充电时执行
        builder.setRequiresCharging(true);
        
        // 设置任务的最小延迟时间（3分钟）
        builder.setMinimumLatency(3 * 60 * 1000);
        
        // 设置任务的最大延迟时间（10分钟）
        builder.setOverrideDeadline(10 * 60 * 1000);
        
        // 设置在设备重启后是否继续执行
        builder.setPersisted(true);
        
        JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            int resultCode = jobScheduler.schedule(builder.build());
            if (resultCode == JobScheduler.RESULT_SUCCESS) {
                Log.d("MainActivity", "Job scheduled successfully!");
            }
        }
    }

}
