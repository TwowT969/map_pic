package org.lxp.mapalbum.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.lxp.mapalbum.framework.mybatis.dataobject.BaseDO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 照片 DO（KSHG 规范 §7.3）。
 *
 * <p>每张照片归属于一个点位（spotId NOT NULL）；关联 SSO 服务（ssoUserId 冗余）。
 * URL/thumbUrl 存 OSS 地址，文件不入库。审核状态支持待审/通过/驳回三级。
 *
 * <p>geo(POINT) 字段同 {@link SpotDO}，暂排除自动映射，后续加 TypeHandler。
 *
 * @author lxp
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("photo")
public class PhotoDO extends BaseDO {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联点位 ID */
    private Long spotId;

    /** 上传用户 ID */
    private Long userId;

    /** SSO 用户标识（冗余字段，跨服务查询用） */
    private String ssoUserId;

    // ===== 存储 =====

    /** OSS 原图 URL */
    private String url;

    /** 缩略图 URL */
    private String thumbUrl;

    // ===== 图片元数据（EXIF + OSS 回写） =====

    /** 图片宽度（px） */
    private Integer width;

    /** 图片高度（px） */
    private Integer height;

    /** 文件大小（byte） */
    private Long sizeBytes;

    /** 图片格式（jpg / png / webp / heic） */
    private String format;

    // ===== 定位 =====

    /** 拍摄纬度（GCJ-02） */
    private BigDecimal lat;

    /** 拍摄经度（GCJ-02） */
    private BigDecimal lng;

    /**
     * 空间点（与 lat/lng 冗余）。
     * MySQL POINT，WKT 格式；当前排除自动映射，空间查询走 Mapper XML。
     */
    @TableField(exist = false)
    private String geo;

    // ===== 拍摄信息 =====

    /** 拍摄时间（从 EXIF 读取） */
    private LocalDateTime shotTime;

    /** 拍摄设备（EXIF Make + Model） */
    private String device;

    /** 用户描述 / 文案 */
    private String description;

    // ===== 审核 =====

    /** 审核状态：0-待审 1-通过 2-驳回 */
    private Integer auditStatus;

    /** 驳回原因 */
    private String auditReason;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核人（关联 user.id） */
    private Long auditor;

    // ===== 展示 =====

    /** 同点位排序（升序，小的在前） */
    private Integer sortOrder;

    /** 查看次数 */
    private Integer viewCount;

    /** 点赞数（冗余字段） */
    private Integer likeCount;

    /** 是否点位封面：0-否 1-是 */
    private Integer isCover;
}
