package org.lxp.mapalbum.controller.app.location;

import org.lxp.mapalbum.controller.app.location.vo.AmapConfigRespVO;
import org.lxp.mapalbum.controller.app.location.vo.LocationRespVO;
import org.lxp.mapalbum.controller.app.location.vo.SuggestRespVO;
import org.lxp.mapalbum.framework.common.pojo.CommonResult;
import org.lxp.mapalbum.framework.common.util.IpUtils;
import org.lxp.mapalbum.service.location.LocationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static org.lxp.mapalbum.framework.common.pojo.CommonResult.success;

/**
 * App - 定位 Controller（KSHG 规范 §5）。
 *
 * <p>按客户端 IP 估算位置，用于地图默认中心；不做登录态校验（首页公开能力）。
 *
 * @author lxp
 */
@RestController
@RequestMapping("/location")
public class LocationController {

    @Resource
    private LocationService locationService;

    /**
     * 按客户端 IP 获取用户位置。
     *
     * @param request HTTP 请求（用于取客户端 IP）
     * @return 位置信息；定位失败回退苏州
     */
    @GetMapping("/ip")
    public CommonResult<LocationRespVO> getByIp(HttpServletRequest request) {
            String clientIp = IpUtils.getClientIp(request);
        return success(locationService.getLocationByIp(clientIp));
    }

    /**
     * 关键词输入提示（高德 inputtips），用于搜索框联想。
     *
     * @param keywords 查询关键词
     * @return 建议列表（含坐标，用于点击后定位）
     */
    @GetMapping("/suggest")
    public CommonResult<List<SuggestRespVO>> suggest(@RequestParam("keywords") String keywords) {
        return success(locationService.suggestByKeyword(keywords));
    }

    /**
     * 下发高德 Web端 JS API 凭证（Key + 安全密钥），供前端 H5 渲染高德地图。
     *
     * <p>首页公开能力，不做登录态校验；凭证由后端集中托管，避免硬编码进前端包。
     *
     * @return JS API Key 与安全密钥
     */
    @GetMapping("/amap-config")
    public CommonResult<AmapConfigRespVO> getAmapConfig() {
        return success(locationService.getJsConfig());
    }
}
