package org.lxp.mapalbum.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.lxp.mapalbum.framework.mybatis.dataobject.BaseDO;

/**
 * App 端日志/埋点 DO（KSHG 规范 §7.3）。
 *
 * @author lxp
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_log")
public class AppLogDO extends BaseDO {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上报用户（未登录可为空） */
    private Long userId;

    /** 级别：info / error */
    private String level;

    /** 标签：crash / track:xxx */
    private String tag;

    /** 摘要信息 */
    private String message;

    /** 堆栈 */
    private String stack;

    /** App 版本号 */
    private String appVersion;

    /** 设备描述 */
    private String device;
}
