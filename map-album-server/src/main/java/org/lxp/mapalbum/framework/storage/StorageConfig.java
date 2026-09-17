package org.lxp.mapalbum.framework.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存储客户端装配配置（KSHG 规范 §3.3：framework 层统一封装）。
 *
 * <p>按 {@code app.storage.type} 装配：
 * <ul>
 *   <li>local（默认，测试阶段）：{@link LocalStorageClient}，文件落盘 {app.upload.path}</li>
 *   <li>oss（生产）：OssClient（原 framework/oss 包，切换时补充装配）</li>
 * </ul>
 * 业务层统一依赖 {@link StorageClient} 接口，切换实现零改动。
 *
 * @author lxp
 */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    public StorageClient localStorageClient(@Value("${app.upload.path:./uploads}") String uploadPath) {
        return new LocalStorageClient(uploadPath);
    }
}
