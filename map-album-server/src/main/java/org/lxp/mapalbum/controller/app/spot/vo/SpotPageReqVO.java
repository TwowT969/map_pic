package org.lxp.mapalbum.controller.app.spot.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.lxp.mapalbum.framework.common.pojo.PageParam;

import java.math.BigDecimal;

/**
 * 点位分页查询 VO（KSHG 规范 §5.5）。
 *
 * <p>支持按关键词/分类/状态/城市/矩形范围筛选。
 *
 * @author lxp
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpotPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;

    /** 关键词搜索（名称模糊匹配） */
    private String name;

    /** 分类筛选 */
    private String category;

    /** 状态筛选 */
    private Integer status;

    /** 城市筛选 */
    private String city;

    /** 地图矩形范围（minLat / maxLat / minLng / maxLng） */
    private BigDecimal minLat;
    private BigDecimal maxLat;
    private BigDecimal minLng;
    private BigDecimal maxLng;

    public SpotPageReqVO() {
    }
}
