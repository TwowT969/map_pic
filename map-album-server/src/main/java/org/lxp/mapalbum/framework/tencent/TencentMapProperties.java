package org.lxp.mapalbum.framework.tencent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯位置服务配置。
 *
 * <p>同一把 Key 可在腾讯位置服务后台开通多个产品：
 * <ul>
 *   <li>JavaScript API GL：H5 端 {@code <map>} 底图（已配在前端 manifest.json）。</li>
 *   <li>WebService：输入提示 / POI 搜索等，后端调用用（本配置）。</li>
 * </ul>
 * 凭证通过环境变量或 application-local.yml 注入，禁止硬编码提交仓库（KSHG 规范 §14.2、红线 #9）。
 *
 * @author lxp
 */
@Data
@Component
@ConfigurationProperties(prefix = "tencent")
public class TencentMapProperties {

    /** 腾讯位置服务 Key（WebService） */
    private String key;
}
