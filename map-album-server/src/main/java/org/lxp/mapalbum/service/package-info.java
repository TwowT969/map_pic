/**
 * 业务层（KSHG 规范 §3.3、§6）。
 *
 * <p>承担业务编排、规则校验、事务控制、持久化与外部依赖（RPC/MQ/Redis）调用。
 * 按业务模块再分包：service/{module}/{module}Service(+Impl)。
 *
 * @author lxp
 */
package org.lxp.mapalbum.service;
