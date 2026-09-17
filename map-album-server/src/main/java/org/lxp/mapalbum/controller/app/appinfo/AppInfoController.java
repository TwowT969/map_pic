package org.lxp.mapalbum.controller.app.appinfo;

import org.lxp.mapalbum.framework.common.pojo.CommonResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static org.lxp.mapalbum.framework.common.pojo.CommonResult.success;

/**
 * App - 版本信息 Controller。
 *
 * <p>GET 公开接口（AuthInterceptor 对 GET 且非"我的"路径放行），
 * 用于 APK 启动时的应用内更新检查。
 *
 * @author lxp
 */
@RestController
@RequestMapping("/app")
public class AppInfoController {

    @Value("${app.version.code:1}")
    private Integer versionCode;

    @Value("${app.version.name:1.0.0}")
    private String versionName;

    @Value("${app.version.url:}")
    private String downloadUrl;

    @Value("${app.version.notes:}")
    private String notes;

    @GetMapping("/version")
    public CommonResult<Map<String, Object>> version() {
        Map<String, Object> data = new HashMap<>();
        data.put("versionCode", versionCode);
        data.put("versionName", versionName);
        data.put("downloadUrl", downloadUrl);
        data.put("notes", notes);
        return success(data);
    }
}
