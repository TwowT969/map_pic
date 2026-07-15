package org.lxp.mapalbum.framework.common.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果（KSHG 规范 §5.5）。
 *
 * @author lxp
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> list;
    private Long total;

    public PageResult() {
    }

    public PageResult(List<T> list, Long total) {
        this.list = list;
        this.total = total;
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 0L);
    }
}
