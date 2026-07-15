package org.lxp.mapalbum.service.location;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.controller.app.location.vo.AmapConfigRespVO;
import org.lxp.mapalbum.controller.app.location.vo.LocationRespVO;
import org.lxp.mapalbum.controller.app.location.vo.SuggestRespVO;
import org.lxp.mapalbum.framework.amap.AmapProperties;
import org.lxp.mapalbum.framework.tencent.TencentMapProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 定位 Service 实现（KSHG 规范 §6）。
 *
 * <p>调用高德 Web 服务 IP 定位接口，按返回的矩形范围取中点作为地图中心；
 * 无 Key、接口异常或无法定位时回退苏州（不以 null 表达失败，异常记 WARN 后回退）。
 *
 * <p>注意：高德 IP 定位返回值类型不稳定——定位成功时 province/city/rectangle 为字符串，
 * 定位不到时为空数组 []。故用 {@link JsonNode} 取值并按文本判断，避免反序列化失败。
 *
 * @author lxp
 */
@Slf4j
@Service
public class LocationServiceImpl implements LocationService {

    /** 高德 Web 服务 IP 定位接口（key/ip 由 RestTemplate 按 URI 变量注入） */
    private static final String AMAP_IP_URL = "https://restapi.amap.com/v3/ip?key={key}&ip={ip}";

    /** 高德 Web 服务 输入提示接口（key/keywords 由 RestTemplate 按 URI 变量注入） */
    private static final String AMAP_INPUTTIPS_URL = "https://restapi.amap.com/v3/assistant/inputtips?key={key}&keywords={keywords}";

    /** 默认回退：苏州（GCJ-02） */
    private static final double SUZHOU_LAT = 31.299379;
    private static final double SUZHOU_LNG = 120.619585;
    private static final String SUZHOU_CITY = "苏州市";
    private static final String SUZHOU_PROVINCE = "江苏省";

    @Resource
    private AmapProperties amapProperties;

    @Resource
    private TencentMapProperties tencentMapProperties;

    @Resource
    private RestTemplate restTemplate;

    @Override
    public LocationRespVO getLocationByIp(String ip) {
        // 回环/非公网 IPv4（含 ::1、127.x、0.0.0.0 等）直接回退，
        // 避免把无效 IP 发给高德 IP API 造成无意义调用和 WARN 日志
        if (!isValidPublicIpv4(ip)) {
            log.debug("[getLocationByIp][ip={}] 本地/回环/非 IPv4 IP，直接回退苏州", ip);
            return suzhou();
        }
        String key = amapProperties.getWeb().getKey();
        if (key == null || key.isEmpty()) {
            log.warn("[getLocationByIp][ip={}] 未配置高德 Web 服务 Key，回退苏州", ip);
            return suzhou();
        }
        try {
            JsonNode node = restTemplate.getForObject(
                    AMAP_IP_URL, JsonNode.class, key, ip == null ? "" : ip);
            if (node != null && "1".equals(node.path("status").asText())) {
                String rectangle = asText(node.get("rectangle"));
                String province = asText(node.get("province"));
                String city = asText(node.get("city"));
                if (!rectangle.isEmpty()) {
                    double[] center = parseCenter(rectangle);
                    if (center != null) {
                        log.info("[getLocationByIp][ip={}] 高德定位 province={}, city={}", ip, province, city);
                        // center = [lng, lat]
                        return new LocationRespVO(center[1], center[0], city, province, "ip");
                    }
                }
                log.warn("[getLocationByIp][ip={}] 高德未返回有效位置(province={}, city={})，回退苏州", ip, province, city);
            } else {
                log.warn("[getLocationByIp][ip={}] 高德接口返回非成功状态(node={})，回退苏州", ip, node);
            }
        } catch (Exception e) {
            log.warn("[getLocationByIp][ip={}] 调用高德 IP 定位异常，回退苏州", ip, e);
        }
        return suzhou();
    }

    @Override
    public List<SuggestRespVO> suggestByKeyword(String keywords) {
        if (keywords == null || keywords.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String key = amapProperties.getWeb().getKey();
        if (key == null || key.isEmpty()) {
            log.warn("[suggestByKeyword][keywords={}] 未配置高德 Web 服务 Key", keywords);
            return Collections.emptyList();
        }
        try {
            JsonNode node = restTemplate.getForObject(
                    AMAP_INPUTTIPS_URL, JsonNode.class, key, keywords.trim());
            if (node == null || !"1".equals(node.path("status").asText())) {
                log.warn("[suggestByKeyword][keywords={}] 高德 inputtips 非成功状态(node={})", keywords, node);
                return Collections.emptyList();
            }
            JsonNode tips = node.get("tips");
            List<SuggestRespVO> list = new ArrayList<>();
            if (tips != null && tips.isArray()) {
                for (JsonNode item : tips) {
                    // location 格式 "lng,lat"
                    String locStr = asText(item.get("location"));
                    if (locStr.isEmpty()) continue;
                    String[] parts = locStr.split(",");
                    if (parts.length < 2) continue;
                    try {
                        double lng = Double.parseDouble(parts[0].trim());
                        double lat = Double.parseDouble(parts[1].trim());
                        list.add(new SuggestRespVO(
                                asText(item.get("id")),
                                asText(item.get("name")),
                                asText(item.get("district")),
                                lng, lat));
                    } catch (NumberFormatException e) {
                        log.debug("[suggestByKeyword] 坐标解析失败 loc={}", locStr);
                    }
                }
            }
            log.debug("[suggestByKeyword][keywords={}] 返回 {} 条建议", keywords, list.size());
            return list;
        } catch (Exception e) {
            log.warn("[suggestByKeyword][keywords={}] 调用高德 inputtips 异常", keywords, e);
            return Collections.emptyList();
        }
    }

    @Override
    public AmapConfigRespVO getJsConfig() {
        AmapProperties.Js js = amapProperties.getJs();
        return new AmapConfigRespVO(js.getKey(), js.getSecurityCode());
    }

    /**
     * 高德字段可能是字符串或空数组 []，统一取文本：字符串返回其值，其他（数组/null）返回空串。
     */
    private String asText(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return "";
        }
        return node.asText();
    }

    private LocationRespVO suzhou() {
        return new LocationRespVO(SUZHOU_LAT, SUZHOU_LNG, SUZHOU_CITY, SUZHOU_PROVINCE, "fallback");
    }

    /**
     * 简单判断是否为可发给高德 IP 定位 API 的公网 IPv4。
     * 排除 null、空、回环(127.x)、本机(0.0.0.0)、IPv6(::1 / 0:0:0:0:0:0:0:1 等)。
     */
    private boolean isValidPublicIpv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        // 只处理点分十进制 IPv4，排除 127.x（回环）和 0.0.0.0（通配）
        return ip.matches("^(?!127\\.|0\\.0\\.0\\.0)\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    }

    /**
     * 解析高德 rectangle "lng1,lat1;lng2,lat2" 取中点。
     *
     * @return [lng, lat]；格式异常返回 null
     */
    private double[] parseCenter(String rectangle) {
        String[] points = rectangle.split(";");
        if (points.length != 2) {
            return null;
        }
        String[] p1 = points[0].split(",");
        String[] p2 = points[1].split(",");
        if (p1.length < 2 || p2.length < 2) {
            return null;
        }
        try {
            double lng1 = Double.parseDouble(p1[0].trim());
            double lat1 = Double.parseDouble(p1[1].trim());
            double lng2 = Double.parseDouble(p2[0].trim());
            double lat2 = Double.parseDouble(p2[1].trim());
            return new double[]{(lng1 + lng2) / 2, (lat1 + lat2) / 2};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
