package org.lxp.mapalbum.framework.storage;

import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.framework.common.exception.ServiceException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.lxp.mapalbum.enums.ErrorCodeConstants.PHOTO_UPLOAD_FAIL;
import static org.lxp.mapalbum.framework.common.exception.ServiceExceptionUtil.exception;

/**
 * 本地磁盘存储客户端（测试阶段实现，KSHG 规范 §3.3）。
 *
 * <p>文件落盘到 {@code app.upload.path} 目录，路径结构即 object key
 * （如 {path}/photos/{spotId}/{ts}_{uuid}.jpg）；
 * 读取由 WebConfig 的 /uploads/** 静态资源映射承接，{@link #signedUrl(String)}
 * 返回相对路径 /uploads/{key}。
 *
 * <p>测试阶段不引入 OSS：由 {@code app.storage.type} 控制装配，
 * 切换回 OSS 时业务代码零改动（统一走 {@link StorageClient} 接口）。
 *
 * @author lxp
 */
@Slf4j
public class LocalStorageClient implements StorageClient {

    /** 存储根目录（对应 app.upload.path） */
    private final Path basePath;

    public LocalStorageClient(String basePath) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        // 启动时确保目录存在，避免首次上传失败
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            log.error("[init][basePath={}] 存储目录创建失败", basePath, e);
        }
    }

    @Override
    public String upload(MultipartFile file, String objectKey) {
        Path target = resolveKey(objectKey);
        try {
            Files.createDirectories(target.getParent());
            // replace 覆盖同名 key（key 含时间戳 + uuid，正常不会重名，防御性兜底）
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            log.info("[upload][key={}, size={}] 本地存储上传成功", objectKey, file.getSize());
            return objectKey;
        } catch (IOException e) {
            log.error("[upload][key={}] 本地存储上传失败", objectKey, e);
            throw exception(PHOTO_UPLOAD_FAIL);
        }
    }

    @Override
    public String uploadBytes(byte[] content, String objectKey, String contentType) {
        Path target = resolveKey(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            log.info("[uploadBytes][key={}, size={}] 本地存储上传成功", objectKey, content.length);
            return objectKey;
        } catch (IOException e) {
            log.error("[uploadBytes][key={}] 本地存储上传失败", objectKey, e);
            throw exception(PHOTO_UPLOAD_FAIL);
        }
    }

    @Override
    public String signedUrl(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            return null;
        }
        // 已是 http(s) 直链或 / 开头相对路径：原样返回（历史数据兼容，与 OssClient 语义一致）
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")
                || objectKey.startsWith("/")) {
            return objectKey;
        }
        return "/uploads/" + objectKey;
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()
                || objectKey.startsWith("http://") || objectKey.startsWith("/")) {
            return;
        }
        Path target = resolveKey(objectKey);
        // 防御路径穿越：解析后必须仍在根目录内
        if (!target.startsWith(basePath)) {
            log.warn("[delete][key={}] 非法路径，跳过删除", objectKey);
            return;
        }
        try {
            Files.deleteIfExists(target);
            log.debug("[delete][key={}] 本地存储删除成功", objectKey);
        } catch (IOException e) {
            // 删除失败仅告警，不阻断业务删除流程（与 OssClient 语义一致）
            log.warn("[delete][key={}] 本地存储删除失败，忽略", objectKey, e);
        }
    }

    private Path resolveKey(String objectKey) {
        return basePath.resolve(objectKey).normalize();
    }
}
