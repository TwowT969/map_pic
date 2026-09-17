package org.lxp.mapalbum.controller.app.spot;

import org.lxp.mapalbum.controller.app.spot.vo.SpotCreateReqVO;
import org.lxp.mapalbum.controller.app.spot.vo.SpotRespVO;
import org.lxp.mapalbum.controller.app.spot.vo.SpotUpdateReqVO;
import org.lxp.mapalbum.framework.common.pojo.CommonResult;
import org.lxp.mapalbum.framework.common.util.SecurityFrameworkUtils;
import org.lxp.mapalbum.service.spot.SpotService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

import static org.lxp.mapalbum.framework.common.pojo.CommonResult.success;

/**
 * App - 点位 Controller（KSHG 规范 §5）。
 *
 * <p>登录态由 AuthInterceptor 统一保障；写操作的 userId 以登录上下文为准，
 * 不信任前端传参（KSHG 规范 §14.1 资源归属校验）。
 *
 * @author lxp
 */
@RestController
@RequestMapping("/spots")
public class SpotController {

    @Resource
    private SpotService spotService;

    /**
     * 查询我的点位（地图标记用）。
     *
     * @param userId 用户 ID（兼容旧调用；有登录态时以登录用户为准）
     * @return 点位列表
     */
    @GetMapping("/mine")
    public CommonResult<List<SpotRespVO>> mine(@RequestParam(value = "userId", required = false) Long userId) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        Long queryUserId = SecurityFrameworkUtils.DEFAULT_USER_ID.equals(loginUserId) ? userId : loginUserId;
        return success(spotService.listByUserId(queryUserId));
    }

    /**
     * 创建点位（前台选点提交）。
     *
     * @param reqVO 创建请求（含名称、坐标、分类等）
     * @return 创建后的点位
     */
    @PostMapping("")
    public CommonResult<SpotRespVO> create(@Validated @RequestBody SpotCreateReqVO reqVO) {
        // userId 以登录上下文为准，不信任前端传参
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(spotService.create(reqVO, userId));
    }

    /**
     * 更新点位信息（部分更新，null 字段不更新）。
     */
    @PutMapping("")
    public CommonResult<SpotRespVO> update(@Validated @RequestBody SpotUpdateReqVO reqVO) {
        return success(spotService.update(reqVO));
    }

    /**
     * 删除点位（逻辑删除，同时清理关联照片）。
     */
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        spotService.delete(id);
        return success(true);
    }
}
