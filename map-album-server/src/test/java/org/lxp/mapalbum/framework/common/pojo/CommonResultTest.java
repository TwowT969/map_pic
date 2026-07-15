package org.lxp.mapalbum.framework.common.pojo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link CommonResult} 单元测试（不加载 Spring 上下文）。
 *
 * @author lxp
 */
class CommonResultTest {

    @Test
    void success_withData_returnsCodeZeroAndData() {
        CommonResult<String> result = CommonResult.success("ok");
        assertEquals(CommonResult.CODE_SUCCESS, result.getCode());
        assertEquals("ok", result.getData());
    }

    @Test
    void success_noData_returnsCodeZeroAndNullData() {
        CommonResult<Void> result = CommonResult.success();
        assertEquals(CommonResult.CODE_SUCCESS, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void error_withCodeAndMsg_returnsErrorResult() {
        CommonResult<Void> result = CommonResult.error(400, "参数错误");
        assertEquals(400, result.getCode());
        assertEquals("参数错误", result.getMsg());
        assertNull(result.getData());
    }
}
