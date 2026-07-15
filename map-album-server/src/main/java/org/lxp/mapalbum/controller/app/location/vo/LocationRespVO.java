package org.lxp.mapalbum.controller.app.location.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * IP 定位响应（KSHG 规范 §8.1 RespVO）。
 *
 * @author lxp
 */
@Data
public class LocationRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 纬度（GCJ-02） */
    private Double lat;

    /** 经度（GCJ-02） */
    private Double lng;

    /** 城市 */
    private String city;

    /** 省份 */
    private String province;

    /** 定位来源：ip=高德IP定位，fallback=回退默认（苏州） */
    private String source;

    public LocationRespVO() {
    }

    public LocationRespVO(Double lat, Double lng, String city, String province, String source) {
        this.lat = lat;
        this.lng = lng;
        this.city = city;
        this.province = province;
        this.source = source;
    }
}
