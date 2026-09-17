package org.lxp.mapalbum.framework.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储客户端接口（KSHG 规范 §3.3：framework 层统一封装）。
 *
 * <p>测试阶段使用本地磁盘实现 {@link LocalStorageClient}；
 * 生产切换阿里云 OSS 时替换为 {@code OssClient} 实现，业务层无感。
 * DB 统一存储对象 key（如 photos/{spotId}/{ts}_{uuid}.jpg）。
 *
 * @author lxp
 */
public interface StorageClient {

    /**
     * 上传文件。
     *
     * @param file      Spring multipart 文件
     * @param objectKey 对象 key（如 photos/{spotId}/{ts}_{uuid}.jpg）
     * @return objectKey（便于调用方落库）
     */
    String upload(MultipartFile file, String objectKey);

    /**
     * 上传内存字节数组（用于服务端生成的缩略图等派生文件）。
     *
     * @param content     文件字节内容
     * @param objectKey   对象 key（如 photos/{spotId}/{ts}_{uuid}_t.jpg）
     * @param contentType HTTP Content-Type（可为 null）
     * @return objectKey（便于调用方落库）
     */
    String uploadBytes(byte[] content, String objectKey, String contentType);

    /**
     * 将对象 key 转为可访问 URL。
     *
     * <p>OSS 实现生成临时签名 URL；本地实现返回 /uploads/{key} 相对路径
     * （由 WebConfig 静态资源映射对外提供服务）。
     *
     * @param objectKey 对象 key
     * @return 访问 URL；key 为空返回 null
     */
    String signedUrl(String objectKey);

    /**
     * 删除对象（物理删除）。非本存储的对象 key（http/相对路径）跳过；失败仅告警不抛异常。
     *
     * @param objectKey 对象 key
     */
    void delete(String objectKey);
}
