package com.mapalbum.app;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.LruCache;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 微信式应用内相册原生支撑：
 * 直读 MediaStore.Images（时间倒序，会话缓存 + 分页），并为每张图
 * 生成/复用 256px 磁盘缩略图（cacheDir/gallery_thumbs/{id}.jpg），
 * 网格不再加载原图 —— 这是上一版加载慢的主因。
 */
@CapacitorPlugin(name = "Gallery")
public class GalleryPlugin extends Plugin {

    private static final String[] PROJECTION = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
    };

    private static final int THUMB_SIZE = 256;

    /** 会话缓存：[id, path, name, dateAdded, size] */
    private List<String[]> cache = null;
    private File thumbDir;
    /** 空缩略图记忆：避免每次翻页重复为坏图生成 */
    private final LruCache<String, Boolean> noThumb = new LruCache<>(512);

    @Override
    public void load() {
        super.load();
        thumbDir = new File(getContext().getCacheDir(), "gallery_thumbs");
        if (!thumbDir.exists()) thumbDir.mkdirs();
    }

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
                int idxId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int idxData = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                int idxDate = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
                int idxSize = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
                while (cursor.moveToNext()) {
                    String path = cursor.getString(idxData);
                    if (path == null || path.isEmpty()) continue;
                    cache.add(new String[] {
                            String.valueOf(cursor.getLong(idxId)),
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

    /** 取/生成 256px 缩略图，返回磁盘路径；失败返回 null（前端降级加载原图） */
    private String thumbPath(String id, String path) {
        String key = id + "@" + new File(path).lastModified();
        File out = new File(thumbDir, id + "_" + Math.abs(key.hashCode()) + ".jpg");
        if (out.exists() && out.length() > 0) return out.getAbsolutePath();
        if (noThumb.get(key) != null) return null;

        Bitmap bm = null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                bm = getContext().getContentResolver().loadThumbnail(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                                .appendPath(id).build(),
                        new android.util.Size(THUMB_SIZE, THUMB_SIZE), null);
            } else {
                bm = MediaStore.Images.Thumbnails.getThumbnail(
                        getContext().getContentResolver(),
                        Long.parseLong(id),
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null);
                if (bm != null && Math.max(bm.getWidth(), bm.getHeight()) > THUMB_SIZE) {
                    bm = ThumbnailUtils.extractThumbnail(bm, THUMB_SIZE, THUMB_SIZE);
                }
            }
        } catch (Exception e) {
            bm = null;
        }
        // 兜底：自己解码原图再缩（个别 ROM loadThumbnail/Thumbnails 都失败时）
        if (bm == null) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, o);
                o.inJustDecodeBounds = false;
                o.inSampleSize = calcSample(o.outWidth, o.outHeight);
                Bitmap full = BitmapFactory.decodeFile(path, o);
                if (full != null) {
                    bm = ThumbnailUtils.extractThumbnail(full, THUMB_SIZE, THUMB_SIZE);
                }
            } catch (Exception e) {
                bm = null;
            }
        }
        if (bm == null) {
            noThumb.put(key, Boolean.TRUE);
            return null;
        }
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bm.compress(Bitmap.CompressFormat.JPEG, 82, fos);
            bm.recycle();
            return out.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private int calcSample(int w, int h) {
        int s = 1;
        while (w / (s * 2) >= THUMB_SIZE && h / (s * 2) >= THUMB_SIZE) s *= 2;
        return s;
    }

    @PluginMethod
    public void list(PluginCall call) {
        int page = call.getInt("page", 0);
        int pageSize = call.getInt("pageSize", 60);
        boolean withThumb = call.getBoolean("thumb", true);

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
        try {
            while (i < total && collected < pageSize) {
                String[] row = cache.get(i);
                i++;
                File f = new File(row[1]);
                if (!f.exists() || !f.canRead()) continue;
                JSObject o = new JSObject();
                o.put("id", row[0]);
                o.put("path", row[1]);
                o.put("name", row[2]);
                o.put("dateAdded", Long.parseLong(row[3]));
                o.put("size", Long.parseLong(row[4]));
                if (withThumb) {
                    String tp = thumbPath(row[0], row[1]);
                    if (tp != null) o.put("thumb", tp);
                }
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
