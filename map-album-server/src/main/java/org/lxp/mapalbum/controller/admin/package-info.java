/**
 * 管理后台访问端 Controller（KSHG 规范 §3.2、§5）。
 *
 * <p>按业务模块再分包：{module}/{module}Controller + {module}/vo。
 * Controller 只做参数接收、校验、调用 Service、返回 {@code CommonResult}。
 *
 * @author lxp
 */
package org.lxp.mapalbum.controller.admin;
