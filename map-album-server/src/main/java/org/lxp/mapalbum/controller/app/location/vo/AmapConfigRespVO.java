package org.lxp.mapalbum.controller.app.location.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 高德 Web端 JS API 凭证下发响应（KSHG 规范 §8.1 RespVO）。
 *
 * <p>后端集中托管 JS_API Key + 安全密钥(securityJsCode)，经此接口下发给前端，
 * 避免硬编码进前端包（AmapProperties §14.2、红线 #9）。
 *
 * @author lxp
 */
@Data
public class AmapConfigRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 高德 Web端 JS API Key */
    private String key;

    /** JS API 安全密钥（securityJsCode） */
    private String securityCode;

    public AmapConfigRespVO() {
    }

    public AmapConfigRespVO(String key, String securityCode) {
        this.key = key;
        this.securityCode = securityCode;
    }
}
