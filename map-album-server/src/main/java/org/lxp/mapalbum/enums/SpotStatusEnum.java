package org.lxp.mapalbum.enums;

import lombok.Getter;

/**
 * 点位状态枚举（KSHG 规范 §9）。
 *
 * @author lxp
 */
@Getter
public enum SpotStatusEnum {

    /** 待审核 */
    PENDING(0, "待审"),
    /** 已上线 */
    ONLINE(1, "上线"),
    /** 已驳回 */
    REJECTED(2, "驳回"),
    /** 已下架 */
    OFFLINE(3, "下架");

    private final Integer code;
    private final String label;

    SpotStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }
}
