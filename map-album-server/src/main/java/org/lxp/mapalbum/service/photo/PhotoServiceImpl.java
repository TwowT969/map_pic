package org.lxp.mapalbum.service.photo;

import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.controller.app.photo.vo.PhotoCreateReqVO;
import org.lxp.mapalbum.controller.app.photo.vo.PhotoRespVO;
import org.lxp.mapalbum.controller.app.photo.vo.PhotoUpdateReqVO;
import org.lxp.mapalbum.dal.dataobject.PhotoDO;
import org.lxp.mapalbum.dal.dataobject.SpotDO;
import org.lxp.mapalbum.dal.mysql.PhotoMapper;
import org.lxp.mapalbum.dal.mysql.SpotMapper;
import org.lxp.mapalbum.framework.common.util.BeanUtils;
import org.lxp.mapalbum.framework.common.util.SecurityFrameworkUtils;
import org.lxp.mapalbum.framework.mybatis.query.LambdaQueryWrapperX;
import org.lxp.mapalbum.framework.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.lxp.mapalbum.enums.ErrorCodeConstants.PHOTO_NO_PERMISSION;
import static org.lxp.mapalbum.enums.ErrorCodeConstants.PHOTO_NOT_EXISTS;
import static org.lxp.mapalbum.enums.ErrorCodeConstants.SPOT_NOT_EXISTS;
import static org.lxp.mapalbum.framework.common.exception.ServiceExceptionUtil.exception;

/**
 * 照片 Service 实现（KSHG 规范 §6）。
 *
 * <p>本地化存储（v1.2.0 起）：图片文件只存设备本地（localPath / localThumbPath），
 * 服务端仅登记元数据；历史记录的 url / thumbUrl 仍走 {@link StorageClient} 签名下发。
 *
 * @author lxp
 */
@Slf4j
@Service
public class PhotoServiceImpl implements PhotoService {

    @Resource
    private PhotoMapper photoMapper;

    @Resource
    private SpotMapper spotMapper;

    @Resource
    private StorageClient storageClient;

    @Override
    public List<PhotoRespVO> listByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapperX<PhotoDO> query = new LambdaQueryWrapperX<PhotoDO>()
                .eqIfPresent(PhotoDO::getUserId, userId)
                .orderByDesc(PhotoDO::getCreateTime);
        List<PhotoDO> list = photoMapper.selectList(query);
        log.debug("[listByUserId][userId={}] 查询到 {} 条照片", userId, list.size());
        List<PhotoRespVO> vos = BeanUtils.toBean(list, PhotoRespVO.class);
        vos.forEach(this::signUrls);
        return vos;
    }

    @Override
    public List<PhotoRespVO> listBySpotId(Long spotId) {
        if (spotId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapperX<PhotoDO> query = new LambdaQueryWrapperX<PhotoDO>()
                .eqIfPresent(PhotoDO::getSpotId, spotId)
                .orderByAsc(PhotoDO::getSortOrder)
                .orderByDesc(PhotoDO::getCreateTime);
        List<PhotoDO> list = photoMapper.selectList(query);
        log.debug("[listBySpotId][spotId={}] 查询到 {} 张照片", spotId, list.size());
        List<PhotoRespVO> vos = BeanUtils.toBean(list, PhotoRespVO.class);
        vos.forEach(this::signUrls);
        return vos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PhotoRespVO create(PhotoCreateReqVO reqVO, Long userId) {
        // 1. 校验点位存在
        SpotDO spot = validateSpotExists(reqVO.getSpotId());

        // 2. 创建照片记录（图片只存设备本地，远程仅登记元数据）
        PhotoDO photo = new PhotoDO();
        photo.setSpotId(reqVO.getSpotId());
        photo.setUserId(userId);
        photo.setLocalPath(reqVO.getLocalPath());
        photo.setLocalThumbPath(reqVO.getLocalThumbPath());
        photo.setLat(spot.getLat());
        photo.setLng(spot.getLng());
        photo.setDescription(reqVO.getDescription());
        photo.setShotTime(parseShotTime(reqVO.getShotTime()));
        photo.setDevice(normalizeDevice(reqVO.getDevice()));
        photo.setAuditStatus(0);
        photo.setSortOrder(0);
        photo.setViewCount(0);
        photo.setLikeCount(0);
        photo.setIsCover(0);
        photo.setCreator(userId);
        photoMapper.insert(photo);

        // 3. 冗余更新点位 photo_count
        updateSpotPhotoCount(spot);

        log.info("[create][id={} spotId={} userId={}] 照片元数据创建 localPath={} shotTime={}",
                photo.getId(), reqVO.getSpotId(), userId, reqVO.getLocalPath(), photo.getShotTime());
        PhotoRespVO vo = BeanUtils.toBean(photo, PhotoRespVO.class);
        signUrls(vo);
        return vo;
    }

    @Override
    public PhotoRespVO update(PhotoUpdateReqVO reqVO) {
        PhotoDO photo = photoMapper.selectById(reqVO.getId());
        if (photo == null) {
            log.warn("[update][id={}] 照片不存在", reqVO.getId());
            throw exception(PHOTO_NOT_EXISTS);
        }
        validateOwnership(photo);
        if (reqVO.getDescription() != null) {
            photo.setDescription(reqVO.getDescription());
        }
        if (reqVO.getSortOrder() != null) {
            photo.setSortOrder(reqVO.getSortOrder());
        }
        if (reqVO.getIsCover() != null) {
            photo.setIsCover(reqVO.getIsCover());
        }
        photoMapper.updateById(photo);
        log.info("[update][id={}] 照片更新 desc={}", photo.getId(),
                photo.getDescription() == null ? null : photo.getDescription().length());
        PhotoRespVO vo = BeanUtils.toBean(photo, PhotoRespVO.class);
        signUrls(vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PhotoDO photo = photoMapper.selectById(id);
        if (photo == null) {
            log.warn("[delete][id={}] 照片不存在", id);
            return;
        }
        // 资源归属校验：仅本人或系统上下文可删（测试阶段放宽为登录即可操作）
        validateOwnership(photo);

        // 逻辑删除照片
        photoMapper.deleteById(id);
        // 物理删除远程存储文件（仅历史记录有；本地化照片文件在设备端，随端删除）
        if (photo.getUrl() != null) {
            storageClient.delete(photo.getUrl());
        }
        if (photo.getThumbUrl() != null && !photo.getThumbUrl().equals(photo.getUrl())) {
            storageClient.delete(photo.getThumbUrl());
        }

        // 更新点位 photo_count
        SpotDO spot = spotMapper.selectById(photo.getSpotId());
        if (spot != null) {
            int newCount = Math.max(0, (spot.getPhotoCount() == null ? 0 : spot.getPhotoCount()) - 1);
            SpotDO update = new SpotDO();
            update.setId(spot.getId());
            update.setPhotoCount(newCount);
            spotMapper.updateById(update);
        }
        log.info("[delete][id={} spotId={}] 照片已删除", id, photo.getSpotId());
    }

    /**
     * 解析拍摄时间：优先 yyyy-MM-dd HH:mm:ss；兼容 ISO；解析失败返回 null（不阻断创建）。
     */
    private LocalDateTime parseShotTime(String shotTime) {
        if (shotTime == null || shotTime.trim().isEmpty()) {
            return null;
        }
        String str = shotTime.trim();
        try {
            return LocalDateTime.parse(str, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignore) {
            // fallthrough：尝试 ISO 格式
        }
        try {
            return LocalDateTime.parse(str);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * 设备信息规范化：去首尾空白、限长 100。
     */
    private String normalizeDevice(String device) {
        if (device == null) {
            return null;
        }
        String d = device.trim();
        return d.isEmpty() ? null : (d.length() > 100 ? d.substring(0, 100) : d);
    }

    /**
     * 校验点位存在并返回 DO（复用查询结果，KSHG 规范 §6.3）。
     */
    private SpotDO validateSpotExists(Long spotId) {
        SpotDO spot = spotMapper.selectById(spotId);
        if (spot == null) {
            log.warn("[validateSpotExists][spotId={}] 点位不存在", spotId);
            throw exception(SPOT_NOT_EXISTS);
        }
        return spot;
    }

    /**
     * 资源归属校验：登录用户仅可操作自己的照片（系统上下文 DEFAULT_USER_ID 豁免）。
     */
    private void validateOwnership(PhotoDO photo) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        if (SecurityFrameworkUtils.DEFAULT_USER_ID.equals(loginUserId)) {
            return;
        }
        if (!loginUserId.equals(photo.getUserId())) {
            log.warn("[validateOwnership][photoId={}, ownerId={}, loginUserId={}] 越权操作被拒绝",
                    photo.getId(), photo.getUserId(), loginUserId);
            throw exception(PHOTO_NO_PERMISSION);
        }
    }

    private void updateSpotPhotoCount(SpotDO spot) {
        SpotDO update = new SpotDO();
        update.setId(spot.getId());
        update.setPhotoCount(spot.getPhotoCount() == null ? 1 : spot.getPhotoCount() + 1);
        spotMapper.updateById(update);
    }

    /**
     * 将 RespVO 中的远程对象 key 转为访问 URL（本地化照片无远程文件，跳过签名）。
     */
    private void signUrls(PhotoRespVO vo) {
        if (vo == null) {
            return;
        }
        if (vo.getUrl() != null) {
            vo.setUrl(storageClient.signedUrl(vo.getUrl()));
        }
        if (vo.getThumbUrl() != null) {
            vo.setThumbUrl(storageClient.signedUrl(vo.getThumbUrl()));
        }
    }
}
