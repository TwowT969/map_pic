package org.lxp.mapalbum.framework.mybatis.dataobject;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 持久化对象基类（KSHG 规范 §7.3）。
 *
 * <p>统一审计字段由 {@code DefaultDBFieldHandler} 自动填充；逻辑删除由 {@code @TableLogic} 拦截。
 * 单租户场景，不含 tenant_id（如后续引入多租户，改继承 TenantBaseDO）。
 * 子类自行声明 {@code @TableId private Long id;} 主键。
 *
 * @author lxp
 */
@Data
public abstract class BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updater;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标志：0-未删除，1-已删除 */
    @TableLogic
    private Boolean deleted;
}
