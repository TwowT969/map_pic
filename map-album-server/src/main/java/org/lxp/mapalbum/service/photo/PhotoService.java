package org.lxp.mapalbum.service.photo;

import org.lxp.mapalbum.controller.app.photo.vo.PhotoCreateReqVO;
import org.lxp.mapalbum.controller.app.photo.vo.PhotoRespVO;
import org.lxp.mapalbum.controller.app.photo.vo.PhotoUpdateReqVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 照片 Service（KSHG 规范 §6）。
 *
 * @author lxp
 */
public interface PhotoService {

    /**
     * 查询用户的所有照片（按创建时间倒序）。
     *
     * @param userId 用户 ID
     * @return 照片列表（不含已逻辑删除的）
     */
    List<PhotoRespVO> listByUserId(Long userId);

    /**
     * 查询某点位下的所有照片（按排序字段升序）。
     *
     * @param spotId 点位 ID
     * @return 照片列表
     */
    List<PhotoRespVO> listBySpotId(Long spotId);

    /**
     * 创建照片记录（本地化存储：图片只存设备本地，远程仅登记元数据）。
     *
     * @param reqVO 照片元数据（点位、备注、拍摄时间、设备、本地路径）
     * @param userId 上传用户 ID
     * @return 创建后的照片
     */
    PhotoRespVO create(PhotoCreateReqVO reqVO, Long userId);

    /**
     * 更新照片展示属性（备注/排序/封面；资源归属校验）。
     *
     * @param reqVO 更新请求
     * @return 更新后的照片
     */
    PhotoRespVO update(PhotoUpdateReqVO reqVO);

    /**
     * 删除照片（逻辑删除），并同步更新点位的 photo_count。
     *
     * @param id 照片 ID
     */
    void delete(Long id);
}
