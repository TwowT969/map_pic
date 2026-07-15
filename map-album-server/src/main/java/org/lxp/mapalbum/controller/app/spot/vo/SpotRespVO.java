package org.lxp.mapalbum.controller.app.spot.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 点位响应 VO（KSHG 规范 §8.1 RespVO）。
 *
 * @author lxp
 */
@Data
public class SpotRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String category;
    private String tags;
    private BigDecimal lat;
    private BigDecimal lng;
    private Long coverPhotoId;
    private String address;
    private String province;
    private String city;
    private String district;
    private Integer photoCount;
    private Integer likeCount;
    private Integer status;
    private LocalDateTime createTime;

    public SpotRespVO() {
    }

    public SpotRespVO(Long id, String name, String description, String category,
                      String tags, BigDecimal lat, BigDecimal lng,
                      Long coverPhotoId, String address, String province,
                      String city, String district, Integer photoCount,
                      Integer likeCount, Integer status, LocalDateTime createTime) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.tags = tags;
        this.lat = lat;
        this.lng = lng;
        this.coverPhotoId = coverPhotoId;
        this.address = address;
        this.province = province;
        this.city = city;
        this.district = district;
        this.photoCount = photoCount;
        this.likeCount = likeCount;
        this.status = status;
        this.createTime = createTime;
    }
}
