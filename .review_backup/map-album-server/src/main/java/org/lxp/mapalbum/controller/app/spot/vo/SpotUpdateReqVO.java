package org.lxp.mapalbum.controller.app.spot.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 点位更新请求 VO（KSHG 规范 §5.1 ReqVO）。
 *
 * <p>只传需要更新的字段；null 字段不更新。
 *
 * @author lxp
 */
@Data
public class SpotUpdateReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "点位 ID 不能为空")
    private Long id;

    private String name;
    private String description;
    private String category;
    private String tags;
    private BigDecimal lat;
    private BigDecimal lng;
    private String address;
    private String province;
    private String city;
    private String district;
    private Integer status;

    public SpotUpdateReqVO() {
    }
}
