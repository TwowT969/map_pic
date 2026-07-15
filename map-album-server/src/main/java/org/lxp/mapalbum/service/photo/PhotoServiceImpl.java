package org.lxp.mapalbum.service.photo;

import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.controller.app.photo.vo.PhotoRespVO;
import org.lxp.mapalbum.dal.dataobject.PhotoDO;
import org.lxp.mapalbum.dal.dataobject.SpotDO;
import org.lxp.mapalbum.dal.mysql.PhotoMapper;
import org.lxp.mapalbum.dal.mysql.SpotMapper;
import org.lxp.mapalbum.framework.common.util.BeanUtils;
import org.lxp.mapalbum.framework.mybatis.query.LambdaQueryWrapperX;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.lxp.mapalbum.enums.ErrorCodeConstants.PHOTO_UPLOAD_FAIL;
import static org.lxp.mapalbum.enums.ErrorCodeConstants.SPOT_NOT_EXISTS;
import static org.lxp.mapalbum.framework.common.exception.ServiceExceptionUtil.exception;

/**
 * 照片 Service 实现（KSHG 规范 §6）。
 *
 * @author lxp
 */
@Slf4j
@Service
public class PhotoServiceImpl implements PhotoService {

    @Value("${app.upload.path:C:\\Users\\DELL\\Desktop\\STOAGE}")
    private String uploadPath;

    @Resource
    private PhotoMapper photoMapper;

    @Resource
    private SpotMapper spotMapper;

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
        return BeanUtils.toBean(list, PhotoRespVO.class);
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
        return BeanUtils.toBean(list, PhotoRespVO.class);
    }

    @Override
    public PhotoRespVO create(MultipartFile file, Long spotId, Long userId, String description) {
        // 1. 校验点位存在
        SpotDO spot = spotMapper.selectById(spotId);
        if (spot == null) {
            log.warn("[create][spotId={}] 点位不存在", spotId);
            throw exception(SPOT_NOT_EXISTS);
        }

        // 2. 保存文件
        String fileUrl = saveFile(file, spotId, userId);

        // 3. 创建照片记录
        PhotoDO photo = new PhotoDO();
        photo.setSpotId(spotId);
        photo.setUserId(userId);
        photo.setUrl(fileUrl);
        photo.setThumbUrl(fileUrl); // 后续可生成缩略图替换
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

        log.info("[create][id={} spotId={} userId={}] 照片创建 url={}",
                photo.getId(), spotId, userId, fileUrl);
        return BeanUtils.toBean(photo, PhotoRespVO.class);
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

    /** 将上传文件持久化到本地 uploads 目录，返回可访问的 URL 路径 */
    private String saveFile(MultipartFile file, Long spotId, Long userId) {
        try {
            // 目录：uploads/photos/{spotId}/
            Path dir = Paths.get(uploadPath, "photos", String.valueOf(spotId));
            Files.createDirectories(dir);

            // 文件名：{timestamp}_{uuid}.{ext}
            String origName = file.getOriginalFilename();
            String ext = "";
            if (origName != null && origName.contains(".")) {
                ext = origName.substring(origName.lastIndexOf('.'));
            }
            String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path target = dir.resolve(fileName);

            file.transferTo(target.toFile());
            // 返回相对路径，前端拼接 BASE_URL 或通过 /uploads/** 访问
            return "/uploads/photos/" + spotId + "/" + fileName;
        } catch (IOException e) {
            log.error("[saveFile][spotId={}] 文件保存失败", spotId, e);
            throw exception(PHOTO_UPLOAD_FAIL);
        }
    }
}
