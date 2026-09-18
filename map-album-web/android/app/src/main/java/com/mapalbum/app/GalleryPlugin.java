package com.mapalbum.app;

import android.database.Cursor;
import android.provider.MediaStore;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 微信式应用内相册选择器的原生支撑：
 * 直读系统相册数据库（MediaStore.Images），按拍摄/添加时间倒序分页返回
 * 图片的真实文件路径，WebView 侧用 _capacitor_file_ 协议直接渲染缩略图。
 * 不拉起任何系统选择器（DocumentsUI / 文件管理器），体验与微信一致。
 *
 * 会话缓存：page=0 时全量刷新一次（仅查 id/path/时间/大小，轻量），
 * 翻页直接切片，滚动加载零查询延迟。
 */
@CapacitorPlugin(name = "Gallery")
public class GalleryPlugin extends Plugin {

    private static final String[] PROJECTION = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
    };

    /** 会话缓存（page=0 刷新） */
    private List<String[]> cache = null; // [path, name, dateAdded, size]

    private void refresh() throws Exception {
        cache = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = getContext().getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    PROJECTION,
                    MediaStore.Images.Media.SIZE + " > 0",
                    null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (cursor != null) {
                int idxData = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                int idxDate = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
                int idxSize = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
                while (cursor.moveToNext()) {
                    String path = cursor.getString(idxData);
                    if (path == null || path.isEmpty()) continue;
                    cache.add(new String[] {
                            path,
                            path.substring(path.lastIndexOf('/') + 1),
                            String.valueOf(cursor.getLong(idxDate)),
                            String.valueOf(cursor.getLong(idxSize))
                    });
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    @PluginMethod
    public void list(PluginCall call) {
        int page = call.getInt("page", 0);
        int pageSize = call.getInt("pageSize", 60);

        if (page == 0 || cache == null) {
            try {
                refresh();
            } catch (SecurityException se) {
                call.reject("没有相册读取权限");
                return;
            } catch (Exception e) {
                call.reject("读取相册失败: " + e.getMessage(), e);
                return;
            }
        }

        int total = cache.size();
        JSArray items = new JSArray();
        int collected = 0;
        int i = Math.max(0, page * pageSize);
        // 切片时校验文件仍存在（只校验本页涉及的行，成本低）
        try {
            while (i < total && collected < pageSize) {
                String[] row = cache.get(i);
                i++;
                File f = new File(row[0]);
                if (!f.exists() || !f.canRead()) continue;
                JSObject o = new JSObject();
                o.put("path", row[0]);
                o.put("name", row[1]);
                o.put("dateAdded", Long.parseLong(row[2]));
                o.put("size", Long.parseLong(row[3]));
                items.put(o);
                collected++;
            }
        } catch (Exception e) {
            call.reject("组装结果失败: " + e.getMessage(), e);
            return;
        }

        JSObject ret = new JSObject();
        ret.put("items", items);
        ret.put("total", total);
        ret.put("hasMore", i < total);
        call.resolve(ret);
    }
}
