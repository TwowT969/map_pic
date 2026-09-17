package org.lxp.mapalbum.controller.app.applog.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * App 端日志/埋点上报请求 VO（KSHG 规范 §5.1 ReqVO）。
 *
 * @author lxp
 */
@Data
public class AppLogReportReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 级别：info / error */
    private String level;

    /** 标签：如 crash / track:upload */
    private String tag;

    /** 摘要信息 */
    private String message;

    /** 堆栈（error 级别，截断存储） */
    private String stack;

    /** App 版本号 */
    private String appVersion;

    /** 设备描述（UserAgent 截断） */
    private String device;

    public AppLogReportReqVO() {
    }
}
