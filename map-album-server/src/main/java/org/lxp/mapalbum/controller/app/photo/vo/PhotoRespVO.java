package org.lxp.mapalbum.controller.app.photo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 照片响应 VO（KSHG 规范 §8.1 RespVO）。
 *
 * @author lxp
 */
@Data
public class PhotoRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long spotId;
    private Long userId;
    private String ssoUserId;
    private String url;
    private String thumbUrl;
    private Integer width;
    private Integer height;
    private Long sizeBytes;
    private String format;
    private java.math.BigDecimal lat;
    private java.math.BigDecimal lng;
    private LocalDateTime shotTime;
    private String device;
    private String description;
    private Integer auditStatus;
    private String auditReason;
    private LocalDateTime auditTime;
    private Long auditor;
    private Integer sortOrder;
    private Integer viewCount;
    private Integer likeCount;
    private Integer isCover;
    private LocalDateTime createTime;

    public PhotoRespVO() {
    }

    public PhotoRespVO(Long id, Long spotId, Long userId, String ssoUserId,
                       String url, String thumbUrl, Integer width, Integer height,
                       Long sizeBytes, String format, java.math.BigDecimal lat,
                       java.math.BigDecimal lng, LocalDateTime shotTime, String device,
                       String description, Integer auditStatus, String auditReason,
                       LocalDateTime auditTime, Long auditor, Integer sortOrder,
                       Integer viewCount, Integer likeCount, Integer isCover,
                       LocalDateTime createTime) {
        this.id = id;
        this.spotId = spotId;
        this.userId = userId;
        this.ssoUserId = ssoUserId;
        this.url = url;
        this.thumbUrl = thumbUrl;
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
        this.format = format;
        this.lat = lat;
        this.lng = lng;
        this.shotTime = shotTime;
        this.device = device;
        this.description = description;
        this.auditStatus = auditStatus;
        this.auditReason = auditReason;
        this.auditTime = auditTime;
        this.auditor = auditor;
        this.sortOrder = sortOrder;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.isCover = isCover;
        this.createTime = createTime;
    }
}
