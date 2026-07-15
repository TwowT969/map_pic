package org.lxp.mapalbum.service.location;

import org.lxp.mapalbum.controller.app.location.vo.AmapConfigRespVO;
import org.lxp.mapalbum.controller.app.location.vo.LocationRespVO;
import org.lxp.mapalbum.controller.app.location.vo.SuggestRespVO;

import java.util.List;

/**
 * 定位 Service（KSHG 规范 §6）。
 *
 * @author lxp
 */
public interface LocationService {

    /**
     * 按 IP 获取用户位置（高德 IP 定位，失败回退苏州）。
     *
     * @param ip 客户端 IP
     * @return 位置信息
     */
    LocationRespVO getLocationByIp(String ip);

    /**
     * 关键词输入提示（高德 inputtips），用于搜索框联想。
     *
     * @param keywords 查询关键词
     * @return 建议列表（无结果或异常返回空列表）
     */
    List<SuggestRespVO> suggestByKeyword(String keywords);

    /**
     * 获取高德 Web端 JS API 凭证（Key + 安全密钥），下发给前端渲染地图用。
     *
     * @return JS API 凭证
     */
    AmapConfigRespVO getJsConfig();
}
