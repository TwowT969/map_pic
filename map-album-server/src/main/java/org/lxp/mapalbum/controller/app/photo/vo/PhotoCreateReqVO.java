package org.lxp.mapalbum.controller.app.photo.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 照片创建请求 VO（KSHG 规范 §5.1 ReqVO）。
 *
 * @author lxp
 */
@Data
public class PhotoCreateReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "关联点位 ID 不能为空")
    private Long spotId;

    @NotNull(message = "上传用户 ID 不能为空")
    private Long userId;

    private String ssoUserId;

    @NotBlank(message = "图片 URL 不能为空")
    private String url;

    @NotNull(message = "拍摄纬度不能为空")
    private BigDecimal lat;

    @NotNull(message = "拍摄经度不能为空")
    private BigDecimal lng;

    private String thumbUrl;
    private Integer width;
    private Integer height;
    private Long sizeBytes;
    private String format;
    private String device;
    private String description;

    public PhotoCreateReqVO() {
    }
}
