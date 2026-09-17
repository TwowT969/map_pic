package org.lxp.mapalbum.framework.oss;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.IOException;
import java.net.URL;
import java.util.Date;

import static org.lxp.mapalbum.enums.ErrorCodeConstants.PHOTO_UPLOAD_FAIL;
import static org.lxp.mapalbum.framework.common.exception.ServiceExceptionUtil.exception;

/**
 * 阿里云 OSS 客户端封装（技术方案 §7.4）。
 *
 * <p>统一封装上传 / 签名 / 删除；{@link OSS} 实例线程安全，全局单例复用。
 * bucket 为私有，读访问需经 {@link #signedUrl(String)} 生成临时签名 URL。
 *
 * @author lxp
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "oss")
public class OssClient {

    @Resource
    private OssProperties properties;

    private volatile OSS oss;

    @PostConstruct
    public void init() {
        // OSSClientBuilder 不校验凭证、不联网，构建本身不会失败；真实调用时才鉴权。
        // 强制 HTTPS：endpoint 不带 scheme 时 SDK 默认走 HTTP，链路代理可能向 200 响应注入 HTML
        // 致 SDK 无法解析（且凭证不应明文走 HTTP）。
        String endpoint = normalizeEndpoint(properties.getEndpoint());
        this.oss = new OSSClientBuilder()
                .build(endpoint, properties.getAccessKeyId(), properties.getAccessKeySecret());
        log.info("[init][endpoint={}, bucket={}] OSS 客户端已初始化",
                endpoint, properties.getBucketName());
    }

    /** endpoint 不带 scheme 时补 https://；已带 http(s):// 原样返回。 */
    private static String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return endpoint;
        }
        String e = endpoint.trim();
        if (e.startsWith("http://") || e.startsWith("https://")) {
            return e;
        }
        return "https://" + e;
    }

    @PreDestroy
    public void destroy() {
        if (oss != null) {
            oss.shutdown();
        }
    }

    /**
     * 上传文件到 OSS。
     *
     * @param file      Spring multipart 文件
     * @param objectKey OSS 对象 key（如 photos/{spotId}/{ts}_{uuid}.jpg）
     * @return objectKey（便于调用方落库）
     */
    public String upload(MultipartFile file, String objectKey) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            oss.putObject(new PutObjectRequest(properties.getBucketName(), objectKey,
                    file.getInputStream(), metadata));
            log.debug("[upload][bucket={}, key={}, size={}] 上传成功",
                    properties.getBucketName(), objectKey, file.getSize());
            return objectKey;
        } catch (IOException e) {
            log.error("[upload][key={}] 读取上传文件流失败", objectKey, e);
            throw exception(PHOTO_UPLOAD_FAIL);
        } catch (ClientException | OSSException e) {
            log.error("[upload][key={}] OSS 上传失败", objectKey, e);
            throw exception(PHOTO_UPLOAD_FAIL);
        }
    }

    /**
     * 为私有 bucket 的对象生成临时签名访问 URL（HTTP GET）。
     *
     * <p>兼容处理，避免历史数据渲染失败：
     * <ul>
     *   <li>null/空 -> null</li>
     *   <li>已是 http(s) 直链 -> 原样返回</li>
     *   <li>以 / 开头的本地相对路径（旧 /uploads 数据）-> 原样返回，前端拼 /api</li>
     *   <li>其余视为 OSS object key -> 签名</li>
     * </ul>
     */
    public String signedUrl(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            return null;
        }
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")
                || objectKey.startsWith("/")) {
            return objectKey;
        }
        Date expiration = new Date(System.currentTimeMillis()
                + properties.getSignedUrlExpire() * 1000L);
        URL url = oss.generatePresignedUrl(properties.getBucketName(), objectKey, expiration);
        return url == null ? null : url.toString();
    }

    /**
     * 删除对象（物理删除）。非 OSS key（http/相对路径）跳过；删除失败仅告警，不抛异常。
     */
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()
                || objectKey.startsWith("http://") || objectKey.startsWith("https://")
                || objectKey.startsWith("/")) {
            return;
        }
        try {
            oss.deleteObject(properties.getBucketName(), objectKey);
            log.debug("[delete][key={}] OSS 删除成功", objectKey);
        } catch (ClientException | OSSException e) {
            log.warn("[delete][key={}] OSS 删除失败，忽略", objectKey, e);
        }
    }
}
