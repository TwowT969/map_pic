package com.mapalbum.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统 Photo Picker 桥接（Android 13+ 原生 / 11-12 依赖系统组件回移）：
 * 网格渲染与内存管理全部由系统相册进程接管，App 不加载任何列表图片；
 * 只在用户选中后拿到 URI，流式复制到应用缓存，再由 JS 侧逐张加载 ——
 * App 侧内存峰值恒为单张。
 */
@CapacitorPlugin(name = "Gallery")
public class GalleryPlugin extends Plugin {

    @PluginMethod
    public void pick(PluginCall call) {
        int want = call.getInt("max", Integer.MAX_VALUE);
        int sysMax = Integer.MAX_VALUE;
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                sysMax = MediaStore.getPickImagesMaxLimit();
            } catch (Exception ignored) {
            }
        }
        int max = Math.min(want, sysMax);

        Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.setType("image/*");
        if (max > 1) {
            intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, max);
        }
        try {
            startActivityForResult(call, intent, "pickResult");
        } catch (Exception e) {
            call.reject("系统相册 Picker 不可用（需要 Android 13+ 或已更新的系统相册组件）");
        }
    }

    @ActivityCallback
    private void pickResult(PluginCall call, ActivityResult result) {
        if (call == null) return; // 用户取消 / 进程重建
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("未选择照片");
            return;
        }
        List<Uri> uris = new ArrayList<>();
        Intent data = result.getData();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) {
            call.reject("未选择照片");
            return;
        }

        // 流式复制到应用缓存：16KB 缓冲，内存峰值恒定；选中授权的 URI 只在此刻使用
        List<String> paths = new ArrayList<>();
        Exception firstErr = null;
        for (Uri u : uris) {
            try {
                String p = copyToCache(u, paths.size());
                if (p != null) paths.add(p);
            } catch (Exception e) {
                if (firstErr == null) firstErr = e;
            }
        }
        if (paths.isEmpty()) {
            call.reject("读取所选照片失败: " + (firstErr != null ? firstErr.getMessage() : "unknown"));
            return;
        }

        JSObject ret = new JSObject();
        JSArray arr = new JSArray();
        try {
            for (String p : paths) arr.put(p);
        } catch (Exception ignored) {
        }
        ret.put("paths", arr);
        ret.put("count", paths.size());
        call.resolve(ret);
    }

    /** 复制单个选中项到 cacheDir/picked/，返回缓存文件路径 */
    private String copyToCache(Uri uri, int index) throws Exception {
        String display = queryDisplayName(uri);
        String safe = display == null ? "img.jpg"
                : display.replaceAll("[^A-Za-z0-9._\\-]", "_");
        if (safe.length() > 80) {
            String ext = safe.substring(safe.lastIndexOf('.') + 1);
            safe = "photo_" + index + "." + (ext.isEmpty() ? "jpg" : ext);
        }
        File dir = new File(getContext().getCacheDir(), "picked");
        if (!dir.exists()) dir.mkdirs();
        File dest = new File(dir, System.currentTimeMillis() + "_" + index + "_" + safe);

        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = getContext().getContentResolver().openInputStream(uri);
            if (in == null) return null;
            out = new FileOutputStream(dest);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return dest.getAbsolutePath();
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }
    }

    private String queryDisplayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContext().getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }
}
