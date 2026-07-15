package org.lxp.mapalbum.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.lxp.mapalbum.framework.mybatis.dataobject.BaseDO;

import java.math.BigDecimal;

/**
 * 点位 DO（KSHG 规范 §7.3）。
 *
 * <p>用户在前台地图上选择/管理的点位。坐标统一 GCJ-02（高德坐标系）；
 * lat/lng 为权威来源，geo 为冗余列配合 MySQL SPATIAL INDEX 加速空间查询。
 *
 * <p>geo(POINT) 字段暂用 {@code @TableField(exist = false)} 排除 MyBatis-Plus 自动映射——
 * MySQL POINT 需要自定义 TypeHandler（后续添加后移除该注解并使用 Mapper XML 手写空间 SQL）。
 *
 * @author lxp
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spot")
public class SpotDO extends BaseDO {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 点位名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 分类（scenic / restaurant / viewpoint / activity / other） */
    private String category;

    /** 标签 JSON 数组（如 ["花海","日出"]） */
    private String tags;

    /** 纬度（GCJ-02） */
    private BigDecimal lat;

    /** 经度（GCJ-02） */
    private BigDecimal lng;

    /**
     * 空间点（与 lat/lng 冗余）。
     * MySQL POINT SRID 4326，WKT 格式如 "POINT(120.619585 31.299379)"。
     * 当前排除自动映射——需要自定义 TypeHandler；空间查询走 Mapper XML 手写 SQL。
     */
    @TableField(exist = false)
    private String geo;

    /** 封面照片 ID（关联 photo 表） */
    private Long coverPhotoId;

    /** 地址描述 */
    private String address;

    /** 省（逆地理编码回填） */
    private String province;

    /** 市（逆地理编码回填） */
    private String city;

    /** 区/县（逆地理编码回填） */
    private String district;

    /** 关联照片数（冗余字段） */
    private Integer photoCount;

    /** 点赞数（冗余字段） */
    private Integer likeCount;

    /** 状态：0-待审 1-上线 2-驳回 3-下架 */
    private Integer status;
}
