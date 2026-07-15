package org.lxp.mapalbum.enums;

import lombok.Getter;

/**
 * 通用状态枚举（KSHG 规范 §9）。
 *
 * <p>用于 user.status 等开关型字段。
 *
 * @author lxp
 */
@Getter
public enum CommonStatusEnum {

    /** 禁用 */
    DISABLED(0, "禁用"),
    /** 正常 */
    ENABLED(1, "正常");

    private final Integer code;
    private final String label;

    CommonStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }
}
