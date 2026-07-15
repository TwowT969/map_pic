package org.lxp.mapalbum;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 地图相册 App 后端启动类。
 *
 * <p>统一扫描 {@code dal.mysql} 包下的 Mapper 接口；业务分层见各包 package-info。
 *
 * @author lxp
 */
@SpringBootApplication
@MapperScan("org.lxp.mapalbum.dal.mysql")
public class MapAlbumApplication {

    public static void main(String[] args) {
        SpringApplication.run(MapAlbumApplication.class, args);
    }
}
