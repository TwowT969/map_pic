package org.lxp.mapalbum.enums;

import lombok.Getter;

/**
 * 审核状态枚举（KSHG 规范 §9）。
 *
 * <p>用于 photo.auditStatus 等审核流程字段。
 *
 * @author lxp
 */
@Getter
public enum AuditStatusEnum {

    /** 待审核 */
    PENDING(0, "待审"),
    /** 审核通过 */
    APPROVED(1, "通过"),
    /** 审核驳回 */
    REJECTED(2, "驳回");

    private final Integer code;
    private final String label;

    AuditStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }
}
