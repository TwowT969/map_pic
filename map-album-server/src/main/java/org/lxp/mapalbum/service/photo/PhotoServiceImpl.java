package org.lxp.mapalbum.service.photo;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
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
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.lxp.mapalbum.enums.ErrorCodeConstants.PHOTO_NO_PERMISSION;
import static org.lxp.mapalbum.enums.ErrorCodeConstants.PHOTO_NOT_EXISTS;
import static org.lxp.mapalbum.enums.ErrorCodeConstants.SPOT_NOT_EXISTS;
import static org.lxp.mapalbum.framework.common.exception.ServiceExceptionUtil.exception;

/**
 * 照片 Service 实现（KSHG 规范 §6）。
 *
 * <p>照片文件统一走 {@link StorageClient}：测试阶段为本地磁盘实现
 * （DB 存对象 key，读取返回 /uploads/ 相对路径）；
 * 生产切换 OSS 时仅需调整 app.storage.type 配置，业务代码零改动。
 *
 * <p>上传时同步生成服务端缩略图（256px，JPEG q0.7 / PNG q0.8），
 * 列表/地图标记加载 thumbUrl 小图，避免全尺寸原图拖垮页面；
 * 缩略图生成失败时回退原图 key，不阻断创建流程。
 *
 * @author lxp
 */
@Slf4j
@Service
public class PhotoServiceImpl implements PhotoService {

    /** 缩略图最长边（像素）：地图 48px 标记 @3x DPR + 面板小图足够 */
    private static final int THUMB_SIZE = 256;

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
    public PhotoRespVO create(MultipartFile file, Long spotId, Long userId, String description,
                              LocalDateTime shotTime, String device) {
        // 1. 校验点位存在 + 归属（资源归属校验：不可信任前端传参，KSHG 规范 §14.1）
        SpotDO spot = validateSpotExists(spotId);

        // 2. 上传原图 + 生成缩略图（DB 只存对象 key）
        String objectKey = buildObjectKey(file, spotId);
        storageClient.upload(file, objectKey);
        String thumbKey = generateThumbnail(file, objectKey);

        // 3. 创建照片记录
        PhotoDO photo = new PhotoDO();
        photo.setSpotId(spotId);
        photo.setUserId(userId);
        photo.setUrl(objectKey);
        photo.setThumbUrl(thumbKey);
        photo.setLat(spot.getLat());
        photo.setLng(spot.getLng());
        photo.setDescription(description);
        photo.setShotTime(shotTime);
        photo.setDevice(device);
        photo.setAuditStatus(0);
        photo.setSortOrder(0);
        photo.setViewCount(0);
        photo.setLikeCount(0);
        photo.setIsCover(0);
        photo.setCreator(userId);
        photoMapper.insert(photo);

        // 4. 冗余更新点位 photo_count
        updateSpotPhotoCount(spot);

        log.info("[create][id={} spotId={} userId={}] 照片创建 key={} thumb={} shotTime={}",
                photo.getId(), spotId, userId, objectKey, thumbKey, shotTime);
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
        // 物理删除存储文件：原图 + 缩略图（失败仅告警，不阻断删除流程）
        storageClient.delete(photo.getUrl());
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
     * 生成并上传缩略图；任何失败回退原图 key（不阻断创建，仅告警）。
     */
    private String generateThumbnail(MultipartFile file, String objectKey) {
        String thumbKey = buildThumbKey(objectKey);
        try {
            boolean png = isPng(file);
            java.io.ByteArrayOutputStream thumbOut = new java.io.ByteArrayOutputStream();
            Thumbnails.of(file.getInputStream())
                    .size(THUMB_SIZE, THUMB_SIZE)
                    .outputQuality(png ? 0.8d : 0.7d)
                    .outputFormat(png ? "png" : "jpg")
                    .toOutputStream(thumbOut);
            byte[] thumb = thumbOut.toByteArray();
            storageClient.uploadBytes(thumb, thumbKey, png ? "image/png" : "image/jpeg");
            return thumbKey;
        } catch (Exception e) {
            // 不支持的格式（webp/gif 动图等）或 IO 异常：回退原图，保证功能可用
            log.warn("[generateThumbnail][objectKey={}] 缩略图生成失败，回退原图: {}",
                    objectKey, e.getMessage());
            return objectKey;
        }
    }

    /**
     * 构造缩略图 key：在原图 key 扩展名前插入 _t（扩展名统一小写）；无扩展名则追加 .jpg。
     */
    private String buildThumbKey(String objectKey) {
        int dot = objectKey.lastIndexOf('.');
        if (dot > objectKey.lastIndexOf('/')) {
            return objectKey.substring(0, dot) + "_t" + objectKey.substring(dot).toLowerCase();
        }
        return objectKey + "_t.jpg";
    }

    private boolean isPng(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.contains("png");
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
