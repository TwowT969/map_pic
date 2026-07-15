/**
 * 定时任务（KSHG 规范 §3.2 异步处理）。
 *
 * <p>无登录上下文的跑批任务，按规范 §7.4.2 走数据权限绕过（{@code DataPermissionUtils.executeIgnore}）并注明原因。
 *
 * @author lxp
 */
package org.lxp.mapalbum.job;
