package com.notework.common.util;

import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;

/**
 * 业务断言工具。
 * <p>
 * 把"校验不通过就抛业务异常"这种到处都要写的判断收敛到一处，
 * 让 Service 里的校验语句保持一行、意图清楚。
 */
public final class BizAssert {

    private BizAssert() {
    }

    /** 条件必须为真，否则抛出业务异常 */
    public static void isTrue(boolean expression, ErrorCode errorCode) {
        if (!expression) {
            throw new BusinessException(errorCode);
        }
    }

    /** 条件必须为真，否则抛出带自定义提示的业务异常 */
    public static void isTrue(boolean expression, ErrorCode errorCode, String message) {
        if (!expression) {
            throw new BusinessException(errorCode, message);
        }
    }

    /** 对象必须非空，否则抛出业务异常 */
    public static void notNull(Object value, ErrorCode errorCode) {
        if (value == null) {
            throw new BusinessException(errorCode);
        }
    }

    /** 对象必须非空，否则抛出带自定义提示的业务异常 */
    public static void notNull(Object value, ErrorCode errorCode, String message) {
        if (value == null) {
            throw new BusinessException(errorCode, message);
        }
    }
}
