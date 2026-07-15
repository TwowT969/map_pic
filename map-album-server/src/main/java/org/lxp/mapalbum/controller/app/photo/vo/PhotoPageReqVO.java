package org.lxp.mapalbum.controller.app.photo.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.lxp.mapalbum.framework.common.pojo.PageParam;

/**
 * 照片分页查询 VO（KSHG 规范 §5.5）。
 *
 * <p>支持按点位/用户/审核状态筛选。
 *
 * @author lxp
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PhotoPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;

    /** 按点位筛选 */
    private Long spotId;

    /** 按上传用户筛选 */
    private Long userId;

    /** 按审核状态筛选 */
    private Integer auditStatus;

    public PhotoPageReqVO() {
    }
}
