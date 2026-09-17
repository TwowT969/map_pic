package org.lxp.mapalbum.service.photo;

import org.lxp.mapalbum.controller.app.photo.vo.PhotoRespVO;
import org.lxp.mapalbum.controller.app.photo.vo.PhotoUpdateReqVO;
import org.springframework.web.multipart.MultipartFile;

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
     * 上传照片并保存记录。
     *
     * @param file        图片文件
     * @param spotId      关联点位 ID
     * @param userId      上传用户 ID
     * @param description 用户描述（可选）
     * @param shotTime    拍摄时间（EXIF，可选）
     * @param device      拍摄设备（EXIF Make+Model，可选）
     * @return 创建后的照片
     */
    PhotoRespVO create(MultipartFile file, Long spotId, Long userId, String description,
                       LocalDateTime shotTime, String device);

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
