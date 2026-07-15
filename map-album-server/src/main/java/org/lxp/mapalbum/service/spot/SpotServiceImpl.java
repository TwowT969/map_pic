package org.lxp.mapalbum.service.spot;

import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.controller.app.spot.vo.SpotCreateReqVO;
import org.lxp.mapalbum.controller.app.spot.vo.SpotRespVO;
import org.lxp.mapalbum.controller.app.spot.vo.SpotUpdateReqVO;
import org.lxp.mapalbum.dal.dataobject.PhotoDO;
import org.lxp.mapalbum.dal.dataobject.SpotDO;
import org.lxp.mapalbum.dal.mysql.PhotoMapper;
import org.lxp.mapalbum.dal.mysql.SpotMapper;
import org.lxp.mapalbum.enums.SpotStatusEnum;
import org.lxp.mapalbum.framework.common.util.BeanUtils;
import org.lxp.mapalbum.framework.mybatis.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

import static org.lxp.mapalbum.enums.ErrorCodeConstants.SPOT_NOT_EXISTS;
import static org.lxp.mapalbum.framework.common.exception.ServiceExceptionUtil.exception;

/**
 * 点位 Service 实现（KSHG 规范 §6）。
 *
 * @author lxp
 */
@Slf4j
@Service
public class SpotServiceImpl implements SpotService {

    @Resource
    private SpotMapper spotMapper;

    @Resource
    private PhotoMapper photoMapper;

    @Override
    public SpotRespVO create(SpotCreateReqVO reqVO, Long userId) {
        SpotDO spot = new SpotDO();
        spot.setName(reqVO.getName());
        spot.setDescription(reqVO.getDescription());
        spot.setCategory(reqVO.getCategory());
        spot.setTags(reqVO.getTags());
        spot.setLat(reqVO.getLat());
        spot.setLng(reqVO.getLng());
        spot.setAddress(reqVO.getAddress());
        spot.setProvince(reqVO.getProvince());
        spot.setCity(reqVO.getCity());
        spot.setDistrict(reqVO.getDistrict());
        spot.setPhotoCount(0);
        spot.setLikeCount(0);
        spot.setStatus(SpotStatusEnum.PENDING.getCode());
        // 手动设置 creator（DefaultDBFieldHandler 用 SecurityFrameworkUtils 返回 0L，JWT 未接入前先传参）
        spot.setCreator(userId);

        spotMapper.insert(spot);
        log.info("[create][id={} name={} lat={} lng={}] 点位创建", spot.getId(), spot.getName(),
                spot.getLat(), spot.getLng());
        return BeanUtils.toBean(spot, SpotRespVO.class);
    }

    @Override
    public List<SpotRespVO> listByUserId(Long userId) {
        if (userId == null) return Collections.emptyList();
        LambdaQueryWrapperX<SpotDO> query = new LambdaQueryWrapperX<SpotDO>()
                .eqIfPresent(SpotDO::getCreator, userId)
                .orderByDesc(SpotDO::getCreateTime);
        List<SpotDO> list = spotMapper.selectList(query);
        log.debug("[listByUserId][userId={}] 查询到 {} 个点位", userId, list.size());
        return BeanUtils.toBean(list, SpotRespVO.class);
    }

    @Override
    public SpotRespVO update(SpotUpdateReqVO reqVO) {
        SpotDO spot = spotMapper.selectById(reqVO.getId());
        if (spot == null) {
            log.warn("[update][id={}] 点位不存在", reqVO.getId());
            throw exception(SPOT_NOT_EXISTS);
        }
        if (reqVO.getName() != null) spot.setName(reqVO.getName());
        if (reqVO.getDescription() != null) spot.setDescription(reqVO.getDescription());
        if (reqVO.getCategory() != null) spot.setCategory(reqVO.getCategory());
        if (reqVO.getTags() != null) spot.setTags(reqVO.getTags());
        if (reqVO.getLat() != null) spot.setLat(reqVO.getLat());
        if (reqVO.getLng() != null) spot.setLng(reqVO.getLng());
        if (reqVO.getAddress() != null) spot.setAddress(reqVO.getAddress());
        if (reqVO.getProvince() != null) spot.setProvince(reqVO.getProvince());
        if (reqVO.getCity() != null) spot.setCity(reqVO.getCity());
        if (reqVO.getDistrict() != null) spot.setDistrict(reqVO.getDistrict());
        if (reqVO.getStatus() != null) spot.setStatus(reqVO.getStatus());

        spotMapper.updateById(spot);
        log.info("[update][id={} name={}] 点位更新", spot.getId(), spot.getName());
        return BeanUtils.toBean(spot, SpotRespVO.class);
    }

    @Override
    public void delete(Long id) {
        SpotDO spot = spotMapper.selectById(id);
        if (spot == null) {
            log.warn("[delete][id={}] 点位不存在", id);
            throw exception(SPOT_NOT_EXISTS);
        }
        // 逻辑删除关联照片
        LambdaQueryWrapperX<PhotoDO> photoQuery = new LambdaQueryWrapperX<PhotoDO>()
                .eqIfPresent(PhotoDO::getSpotId, id);
        List<PhotoDO> photos = photoMapper.selectList(photoQuery);
        for (PhotoDO photo : photos) {
            photoMapper.deleteById(photo.getId());
        }
        // 逻辑删除点位
        spotMapper.deleteById(id);
        log.info("[delete][id={} name={}] 点位及 {} 张关联照片已删除", id, spot.getName(), photos.size());
    }
}
