package org.lxp.mapalbum.enums;

import lombok.Getter;

/**
 * 点位分类枚举（KSHG 规范 §9）。
 *
 * @author lxp
 */
@Getter
public enum SpotCategoryEnum {

    /** 景点 */
    SCENIC("scenic", "景点"),
    /** 餐饮 */
    RESTAURANT("restaurant", "餐饮"),
    /** 观景台 */
    VIEWPOINT("viewpoint", "观景台"),
    /** 活动 */
    ACTIVITY("activity", "活动"),
    /** 其他 */
    OTHER("other", "其他");

    private final String code;
    private final String label;

    SpotCategoryEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
