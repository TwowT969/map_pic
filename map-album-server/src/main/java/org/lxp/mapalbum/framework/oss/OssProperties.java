package org.lxp.mapalbum.framework.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置（技术方案 §7.4：照片存储）。
 *
 * <p>凭证通过环境变量或 application-local.yml 注入，禁止硬编码提交仓库
 * （KSHG 规范 §14.2、红线 #9）。bucket 建议设为「私有」，读访问走签名 URL
 * （见 {@link OssClient#signedUrl(String)}），有效期由 {@link #signedUrlExpire} 控制。
 *
 * @author lxp
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /** Endpoint（含地域，如 oss-cn-beijing.aliyuncs.com） */
    private String endpoint;

    /** AccessKey ID */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /** 签名 URL 有效期（秒），默认 1 小时 */
    private Long signedUrlExpire = 3600L;
}
