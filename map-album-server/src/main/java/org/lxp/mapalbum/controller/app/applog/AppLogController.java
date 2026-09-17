package org.lxp.mapalbum.controller.app.applog;

import org.lxp.mapalbum.controller.app.applog.vo.AppLogReportReqVO;
import org.lxp.mapalbum.dal.dataobject.AppLogDO;
import org.lxp.mapalbum.dal.mysql.AppLogMapper;
import org.lxp.mapalbum.framework.common.pojo.CommonResult;
import org.lxp.mapalbum.framework.common.util.SecurityFrameworkUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

import static org.lxp.mapalbum.framework.common.pojo.CommonResult.success;

/**
 * App - 崩溃/埋点上报 Controller。
 *
 * <p>POST 需登录（AuthInterceptor 统一拦截）；未登录时由端上缓存，登录后补报。
 * 单次最多入库 50 条，超出截断。
 *
 * @author lxp
 */
@RestController
@RequestMapping("/applog")
public class AppLogController {

    private static final int MAX_BATCH = 50;
    private static final int MAX_MESSAGE = 1000;
    private static final int MAX_STACK = 4000;
    private static final int MAX_DEVICE = 200;
    private static final int MAX_TAG = 64;
    private static final int MAX_VERSION = 32;

    @Resource
    private AppLogMapper appLogMapper;

    @PostMapping("")
    public CommonResult<Boolean> report(@RequestBody List<AppLogReportReqVO> logs) {
        if (logs == null || logs.isEmpty()) {
            return success(true);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        int count = Math.min(logs.size(), MAX_BATCH);
        for (int i = 0; i < count; i++) {
            AppLogReportReqVO vo = logs.get(i);
            if (vo == null) {
                continue;
            }
            AppLogDO entity = new AppLogDO();
            entity.setUserId(userId);
            entity.setLevel(clip(vo.getLevel(), 16));
            entity.setTag(clip(vo.getTag(), MAX_TAG));
            entity.setMessage(clip(vo.getMessage(), MAX_MESSAGE));
            entity.setStack(clip(vo.getStack(), MAX_STACK));
            entity.setAppVersion(clip(vo.getAppVersion(), MAX_VERSION));
            entity.setDevice(clip(vo.getDevice(), MAX_DEVICE));
            appLogMapper.insert(entity);
        }
        return success(true);
    }

    private String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : (t.length() > max ? t.substring(0, max) : t);
    }
}
