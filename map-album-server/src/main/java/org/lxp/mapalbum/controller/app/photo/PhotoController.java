package org.lxp.mapalbum.controller.app.photo;

import org.lxp.mapalbum.controller.app.photo.vo.PhotoRespVO;
import org.lxp.mapalbum.framework.common.pojo.CommonResult;
import org.lxp.mapalbum.framework.common.util.SecurityFrameworkUtils;
import org.lxp.mapalbum.service.photo.PhotoService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

import static org.lxp.mapalbum.framework.common.pojo.CommonResult.success;

/**
 * App - 照片 Controller（KSHG 规范 §5）。
 *
 * <p>写操作的 userId 以登录上下文为准，不信任前端传参（KSHG 规范 §14.1）。
 *
 * @author lxp
 */
@RestController
@RequestMapping("/photos")
public class PhotoController {

    @Resource
    private PhotoService photoService;

    /**
     * 查询我的所有照片（地图标记用，含坐标与缩略图）。
     *
     * @param userId 用户 ID（兼容旧调用；有登录态时以登录用户为准）
     * @return 照片列表
     */
    @GetMapping("/mine")
    public CommonResult<List<PhotoRespVO>> mine(@RequestParam(value = "userId", required = false) Long userId) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        Long queryUserId = SecurityFrameworkUtils.DEFAULT_USER_ID.equals(loginUserId) ? userId : loginUserId;
        return success(photoService.listByUserId(queryUserId));
    }

    /**
     * 查询某点位下的所有照片。
     */
    @GetMapping("/spot/{spotId}")
    public CommonResult<List<PhotoRespVO>> listBySpot(@PathVariable Long spotId) {
        return success(photoService.listBySpotId(spotId));
    }

    /**
     * 上传照片并创建记录（multipart 表单）。
     *
     * @param file        图片文件
     * @param spotId      关联点位 ID
     * @param userId      上传用户 ID（已废弃，以登录上下文为准；兼容保留参数位）
     * @param description 用户描述（可选）
     * @return 创建后的照片
     */
    @PostMapping("")
    public CommonResult<PhotoRespVO> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam("spotId") Long spotId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "description", required = false) String description) {
        // userId 以登录上下文为准，不信任前端传参
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        return success(photoService.create(file, spotId, loginUserId, description));
    }

    /**
     * 删除照片（逻辑删除，仅本人照片可删）。
     */
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        photoService.delete(id);
        return success(true);
    }
}
