package org.lxp.mapalbum.controller.app.location.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 输入提示（inputtips）单条建议（KSHG 规范 §8.1 RespVO）。
 *
 * @author lxp
 */
@Data
public class SuggestRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 高德数据 ID（POI/bus/busline id） */
    private String id;

    /** 名称 */
    private String name;

    /** 所属区域（省+市+区） */
    private String district;

    /** 经度（GCJ-02） */
    private Double lng;

    /** 纬度（GCJ-02） */
    private Double lat;

    public SuggestRespVO() {
    }

    public SuggestRespVO(String id, String name, String district, Double lng, Double lat) {
        this.id = id;
        this.name = name;
        this.district = district;
        this.lng = lng;
        this.lat = lat;
    }
}
