package org.lxp.mapalbum.framework.amap;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 高德地图配置（技术方案 §2：地图 SDK 高德）。
 *
 * <p>高德控制台分两类 Key，用途不同，切勿混用：
 * <ul>
 *   <li><b>Web服务（WEB_API）</b>：服务端 HTTP API 调用，放后端，不暴露给前端。</li>
 *   <li><b>Web端（JS_API）</b>：前端 JS API 渲染地图，Key + 安全密钥(securityJsCode) 在前端使用；
 *       后端集中托管，可经接口下发给前端，避免硬编码进前端包。</li>
 * </ul>
 * 凭证通过环境变量或 application-local.yml 注入，禁止硬编码提交仓库（KSHG 规范 §14.2、红线 #9）。
 *
 * @author lxp
 */
@Data
@Component
@ConfigurationProperties(prefix = "amap")
public class AmapProperties {

    /** Web服务 API（后端 HTTP API 用） */
    private Web web = new Web();

    /** Web端 JS API（前端渲染地图用） */
    private Js js = new Js();

    /**
     * Web服务 API 凭证（后端用）。
     */
    @Data
    public static class Web {
        /** Web服务 Key */
        private String key;
    }

    /**
     * Web端 JS API 凭证（前端用）。
     */
    @Data
    public static class Js {
        /** JS API Key */
        private String key;
        /** JS API 安全密钥（securityJsCode） */
        private String securityCode;
    }
}
