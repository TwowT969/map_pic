package org.lxp.mapalbum.framework.common.pojo;

import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询基础参数（KSHG 规范 §5.5、附录 A.6）。
 *
 * @author lxp
 */
@Data
public class PageParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Integer DEFAULT_PAGE_NO = 1;
    private static final Integer DEFAULT_PAGE_SIZE = 10;

    private Integer pageNo = DEFAULT_PAGE_NO;
    private Integer pageSize = DEFAULT_PAGE_SIZE;
}
