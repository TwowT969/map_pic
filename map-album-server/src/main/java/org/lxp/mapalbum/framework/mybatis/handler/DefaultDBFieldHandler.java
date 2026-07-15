package org.lxp.mapalbum.framework.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.lxp.mapalbum.framework.common.util.SecurityFrameworkUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充（KSHG 规范 §7.3：creator/createTime/updater/updateTime 由框架统一填充）。
 *
 * @author lxp
 */
@Component
public class DefaultDBFieldHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        strictInsertFill(metaObject, "creator", Long.class, userId);
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updater", Long.class, userId);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updater", Long.class, SecurityFrameworkUtils.getLoginUserId());
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
