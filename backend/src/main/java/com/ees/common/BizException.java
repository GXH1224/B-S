package com.ees.common;

/**
 * 业务异常。所有可预期的失败都应抛出本异常，由 GlobalExceptionHandler 统一转换。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object payload;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.payload = null;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.payload = null;
    }

    public BizException(ErrorCode errorCode, String message, Object payload) {
        super(message);
        this.errorCode = errorCode;
        this.payload = payload;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.payload = null;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getPayload() {
        return payload;
    }

    public static BizException of(String message) {
        return new BizException(ErrorCode.BUSINESS_ERROR, message);
    }

    public static BizException param(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }
}
