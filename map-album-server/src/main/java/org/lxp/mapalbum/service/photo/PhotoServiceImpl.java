package org.lxp.mapalbum.service.photo;

import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.controller.app.photo.vo.PhotoRespVO;
import org.lxp.mapalbum.dal.dataobject.PhotoDO;
import org.lxp.mapalbum.dal.dataobject.SpotDO;
import org.lxp.mapalbum.dal.mysql.PhotoMapper;
import org.lxp.mapalbum.dal.mysql.SpotMapper;
import org.lxp.mapalbum.framework.common.util.BeanUtils;
import org.lxp.mapalbum.framework.mybatis.query.LambdaQueryWrapperX;
import org.lxp.mapalbum.framework.oss.OssClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.lxp.mapalbum.enums.ErrorCodeConstants.SPOT_NOT_EXISTS;
import static org.lxp.mapalbum.framework.common.exception.ServiceExceptionUtil.exception;

/**
 * 照片 Service 实现（KSHG 规范 §6）。
 *
 * <p>照片文件存储在阿里云 OSS（私有 bucket），DB 仅存 OSS object key；
 * 读取时由 {@link OssClient#signedUrl(String)} 生成临时签名 URL 下发前端。
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
    private OssClient ossClient;

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
        // 1. 校验点位存在
        SpotDO spot = spotMapper.selectById(spotId);
        if (spot == null) {
            log.warn("[create][spotId={}] 点位不存在", spotId);
            throw exception(SPOT_NOT_EXISTS);
        }

        // 2. 上传到 OSS，拿到 object key（DB 只存 key，不存会过期的签名 URL）
        String objectKey = buildObjectKey(file, spotId);
        ossClient.upload(file, objectKey);

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
        // 手动设置 creator（避免 DefaultDBFieldHandler 用 0L 覆盖）
        photo.setCreator(userId);
        photoMapper.insert(photo);

        // 更新点位 photo_count
        SpotDO update = new SpotDO();
        update.setId(spotId);
        update.setPhotoCount(spot.getPhotoCount() == null ? 1 : spot.getPhotoCount() + 1);
        spotMapper.updateById(update);

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
        // 逻辑删除照片
        photoMapper.deleteById(id);
        // 物理删除 OSS 文件（失败仅告警，不阻断删除流程）
        ossClient.delete(photo.getUrl());

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

    /** 构造 OSS object key：photos/{spotId}/{timestamp}_{uuid8}.{ext} */
    private String buildObjectKey(MultipartFile file, Long spotId) {
        String origName = file.getOriginalFilename();
        String ext = "";
        if (origName != null && origName.contains(".")) {
            ext = origName.substring(origName.lastIndexOf('.'));
        }
        return "photos/" + spotId + "/" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8) + ext;
    }

    /** 将 RespVO 中的 OSS object key 转为临时签名 URL（私有 bucket 读访问）。 */
    private void signUrls(PhotoRespVO vo) {
        if (vo == null) {
            return;
        }
        vo.setUrl(ossClient.signedUrl(vo.getUrl()));
        vo.setThumbUrl(ossClient.signedUrl(vo.getThumbUrl()));
    }
}
