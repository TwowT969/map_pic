/**
 * MyBatis Mapper 接口（KSHG 规范 §3.3、§7.1）。
 *
 * <p>业务 Mapper 继承 {@link org.lxp.mapalbum.framework.mybatis.mapper.BaseMapperX}。
 * 常规条件查询用 {@code LambdaQueryWrapperX}，复杂查询走 {@code resources/mapper/*.xml}；
 * 禁止在 Mapper 写业务规则。由启动类 {@code @MapperScan} 统一扫描。
 *
 * @author lxp
 */
package org.lxp.mapalbum.dal.mysql;
