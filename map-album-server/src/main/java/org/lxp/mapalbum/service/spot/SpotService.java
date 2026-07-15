package org.lxp.mapalbum.service.spot;

import org.lxp.mapalbum.controller.app.spot.vo.SpotCreateReqVO;
import org.lxp.mapalbum.controller.app.spot.vo.SpotRespVO;
import org.lxp.mapalbum.controller.app.spot.vo.SpotUpdateReqVO;

import java.util.List;

/**
 * 点位 Service（KSHG 规范 §6）。
 *
 * @author lxp
 */
public interface SpotService {

    /**
     * 创建点位（用户在地图上选点后提交）。
     *
     * @param reqVO 创建请求
     * @param userId 创建人 ID
     * @return 创建后的点位
     */
    SpotRespVO create(SpotCreateReqVO reqVO, Long userId);

    /**
     * 查询某用户创建的所有点位。
     *
     * @param userId 用户 ID
     * @return 点位列表
     */
    List<SpotRespVO> listByUserId(Long userId);

    /**
     * 更新点位信息（部分更新，null 字段不更新）。
     *
     * @param reqVO 更新请求
     * @return 更新后的点位
     */
    SpotRespVO update(SpotUpdateReqVO reqVO);

    /**
     * 删除点位（逻辑删除），同时清理关联照片。
     *
     * @param id 点位 ID
     */
    void delete(Long id);
}
