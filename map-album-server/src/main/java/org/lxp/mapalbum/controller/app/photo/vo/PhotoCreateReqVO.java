package org.lxp.mapalbum.controller.app.photo.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 照片创建请求 VO（本地化存储：图片文件只存设备本地，远程仅登记元数据）。
 *
 * @author lxp
 */
@Data
public class PhotoCreateReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "关联点位 ID 不能为空")
    private Long spotId;

    /** 用户描述 / 备注 */
    private String description;

    /** 拍摄时间（yyyy-MM-dd HH:mm:ss，来自 EXIF，可选） */
    private String shotTime;

    /** 拍摄设备（EXIF Make + Model，可选） */
    private String device;

    /** 设备本地原图路径 */
    private String localPath;

    /** 设备本地缩略图路径 */
    private String localThumbPath;
}
