/**
 * App 端访问端 Controller（KSHG 规范 §3.2、§5）。
 *
 * <p>面向手机端（地图主页/拍照上传/我的相册/景点详情/登录）的接口，按业务模块再分包。
 * Controller 只做参数接收、校验、调用 Service、返回 {@code CommonResult}。
 *
 * @author lxp
 */
package org.lxp.mapalbum.controller.app;
