package org.lxp.mapalbum.controller.app.spot.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 点位创建请求 VO（KSHG 规范 §5.1 ReqVO）。
 *
 * @author lxp
 */
@Data
public class SpotCreateReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "点位名称不能为空")
    private String name;

    @NotNull(message = "纬度不能为空")
    private BigDecimal lat;

    @NotNull(message = "经度不能为空")
    private BigDecimal lng;

    /** 创建人 ID（前端传入，后续接入 JWT 后由后端从 token 获取） */
    private Long userId;

    private String description;
    private String category;
    private String tags;
    private String address;
    private String province;
    private String city;
    private String district;

    public SpotCreateReqVO() {
    }
}
