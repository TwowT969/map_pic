package org.lxp.mapalbum.controller.app.photo.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 照片更新请求 VO（KSHG 规范 §5.1 ReqVO）。
 *
 * <p>只传需要更新的字段；null 字段不更新。主要用于更新描述、封面标记、排序等展示属性。
 *
 * @author lxp
 */
@Data
public class PhotoUpdateReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "照片 ID 不能为空")
    private Long id;

    private String description;
    private Integer sortOrder;
    private Integer isCover;

    public PhotoUpdateReqVO() {
    }
}
