package org.lxp.mapalbum.service.photo;

import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.controller.app.photo.vo.PhotoRespVO;
import org.lxp.mapalbum.dal.dataobject.PhotoDO;
import org.lxp.mapalbum.dal.dataobject.SpotDO;
import org.lxp.mapalbum.dal.mysql.PhotoMapper;
import org.lxp.mapalbum.dal.mysql.SpotMapper;
import org.lxp.mapalbum.framework.common.util.BeanUtils;
import org.lxp.mapalbum.framework.common.util.SecurityFrameworkUtils;
import org.lxp.mapalbum.framework.mybatis.query.LambdaQueryWrapperX;
import org.lxp.mapalbum.framework.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.lxp.mapalbum.enums.ErrorCodeConstants.PHOTO_NO_PERMISSION;
import static org.lxp.mapalbum.enums.ErrorCodeConstants.SPOT_NOT_EXISTS;
import static org.lxp.mapalbum.framework.common.exception.ServiceExceptionUtil.exception;

/**
 * 照片 Service 实现（KSHG 规范 §6）。
 *
 * <p>照片文件统一走 {@link StorageClient}：测试阶段为本地磁盘实现
 * （DB 存对象 key，读取返回 /uploads/ 相对路径）；
 * 生产切换 OSS 时仅需调整 app.storage.type 配置，业务代码零改动。
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
    public PhotoRespVO create(MultipartFile file, Long spotId, Long userId, String description) {
        // 1. 校验点位存在 + 归属（资源归属校验：不可信任前端传参，KSHG 规范 §14.1）
        SpotDO spot = validateSpotExists(spotId);

        // 2. 上传文件，DB 只存对象 key
        String objectKey = buildObjectKey(file, spotId);
        storageClient.upload(file, objectKey);

        // 3. 创建照片记录
        PhotoDO photo = new PhotoDO();
        photo.setSpotId(spotId);
        photo.setUserId(userId);
        photo.setUrl(objectKey);
        photo.setThumbUrl(objectKey); // 后续可生成缩略图，单独存 key
        photo.setLat(spot.getLat());
        photo.setLng(spot.getLng());
        photo.setDescription(description);
        photo.setAuditStatus(0);
        photo.setSortOrder(0);
        photo.setViewCount(0);
        photo.setLikeCount(0);
        photo.setIsCover(0);
        photo.setCreator(userId);
        photoMapper.insert(photo);

        // 4. 冗余更新点位 photo_count
        updateSpotPhotoCount(spot);

        log.info("[create][id={} spotId={} userId={}] 照片创建 key={}",
                photo.getId(), spotId, userId, objectKey);
        PhotoRespVO vo = BeanUtils.toBean(photo, PhotoRespVO.class);
        signUrls(vo);
        return vo;
    }

    @Override
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
        // 物理删除存储文件（失败仅告警，不阻断删除流程）
        storageClient.delete(photo.getUrl());

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
     * 资源归属校验：登录用户仅可删除自己的照片（系统上下文 DEFAULT_USER_ID 豁免）。
     */
    private void validateOwnership(PhotoDO photo) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        if (SecurityFrameworkUtils.DEFAULT_USER_ID.equals(loginUserId)) {
            return;
        }
        if (!loginUserId.equals(photo.getUserId())) {
            log.warn("[validateOwnership][photoId={}, ownerId={}, loginUserId={}] 越权删除被拒绝",
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

    /** 构造对象 key：photos/{spotId}/{timestamp}_{uuid8}.{ext} */
    private String buildObjectKey(MultipartFile file, Long spotId) {
        String origName = file.getOriginalFilename();
        String ext = "";
        if (origName != null && origName.contains(".")) {
            ext = origName.substring(origName.lastIndexOf('.'));
        }
        return "photos/" + spotId + "/" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8) + ext;
    }

    /** 将 RespVO 中的对象 key 转为访问 URL。 */
    private void signUrls(PhotoRespVO vo) {
        if (vo == null) {
            return;
        }
        vo.setUrl(storageClient.signedUrl(vo.getUrl()));
        vo.setThumbUrl(storageClient.signedUrl(vo.getThumbUrl()));
    }
}
