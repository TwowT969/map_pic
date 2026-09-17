package com.mapalbum.app;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

/**
 * 地图相册主 Activity。
 *
 * 配置 WebView 以支持高德 JS API 2.0、定位、相机和文件存储。
 * Capacitor 8.x 自动注册所有 npm 安装的插件，无需手动 registerPlugin()。
 */
public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // WebView 在 BridgeActivity.onCreate 后初始化，配置在 onStart 中完成
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWebView();
    }

    @Override
    public void onResume() {
        super.onResume();
        // onResume 时再确认一次（从后台恢复时状态可能丢失）
        configureWebView();
    }

    private void configureWebView() {
        WebView webView = getBridge().getWebView();
        if (webView == null) return;

        WebSettings settings = webView.getSettings();
        // 启用 JavaScript（默认已开启，显式确认）
        settings.setJavaScriptEnabled(true);
        // 启用定位（高德 JS API Geolocation 插件需要）
        settings.setGeolocationEnabled(true);
        settings.setGeolocationDatabasePath(getFilesDir().getPath());
        // DOM Storage（高德缓存地图瓦片）
        settings.setDomStorageEnabled(true);
        // 应用缓存数据库（AppCache API 已在 Android 9+ 移除，DomStorage + Database 已覆盖）
        settings.setDatabaseEnabled(true);
        // 文件访问（Capacitor 从 assets 加载本地文件）
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        // 允许混合内容（开发期 HTTP API，生产用 HTTPS 后可去掉）
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        // 适应用户 Agent 标记（高德地图兼容）
        String ua = settings.getUserAgentString();
        if (ua != null && !ua.contains("MapAlbumApp")) {
            settings.setUserAgentString(ua + " MapAlbumApp/1.0");
        }
        // 文本缩放 100%（H5 已做移动端适配，不再额外缩放）
        settings.setTextZoom(100);
        // 启用平滑缩放
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
    }
}
