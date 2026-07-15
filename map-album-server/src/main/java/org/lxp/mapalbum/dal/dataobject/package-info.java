/**
 * 持久化对象 DO（KSHG 规范 §3.3、§7、§8.1）。
 *
 * <p>DO 仅出现在 DAL 层与 Service 内部，禁止通过 HTTP 返回。
 * 业务 DO 继承 {@link org.lxp.mapalbum.framework.mybatis.dataobject.BaseDO}。
 *
 * @author lxp
 */
package org.lxp.mapalbum.dal.dataobject;
