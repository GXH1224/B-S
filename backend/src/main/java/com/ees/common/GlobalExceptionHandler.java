package com.ees.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理。生产环境不返回详细堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException e) {
        log.warn("业务异常: code={} msg={}", e.getErrorCode().getCode(), e.getMessage());
        ApiResponse<Object> body = ApiResponse.fail(e.getErrorCode().getCode(), e.getMessage());
        if (e.getPayload() != null) {
            body.setData(e.getPayload());
        }
        HttpStatus status = switch (e.getErrorCode()) {
            case UNAUTHORIZED, TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND, DATASOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(body);
    }

    /** 参数校验失败：返回字段级错误，对应方案 8.3 “输入错误”。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Object>> handleValidation(BindException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String msg = fieldErrors.entrySet().stream()
                .map(en -> en.getKey() + ": " + en.getValue())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(),
                "参数校验失败 -> " + msg).withData(fieldErrors));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Object>> handleMissingParam(Exception e) {
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.PARAM_INVALID, "缺少必要参数: " + e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.PARAM_INVALID, "请求体格式错误，无法解析 JSON"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.FILE_TOO_LARGE, "上传文件超出大小限制"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN, "无权访问该资源"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.PARAM_INVALID, "请求方法不支持: " + e.getMethod()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandler(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ErrorCode.NOT_FOUND, "接口不存在: " + e.getRequestURL()));
    }

    /**
     * 静态资源不存在：返回 404，且不打印堆栈。
     *
     * <p>为什么必须单独处理：Spring 6.1 起请求不到的静态文件会抛
     * {@link NoResourceFoundException}。如果不单独映射，它会落到
     * {@link #handleOther(Exception)} —— 记录一整串 ERROR 堆栈并返回 <b>500</b>，有两个害处：</p>
     *
     * <ol>
     *   <li><b>误导排障</b>：前端重新构建后 chunk 文件名（带内容 hash）会变，
     *       浏览器若仍持有旧页面就会去取已经不存在的旧文件。返回 500 会让人误以为是后端故障，
     *       实际只是"文件不存在"，应当是 404。</li>
     *   <li><b>污染日志、违反交付要求</b>：浏览器自动请求 favicon.ico 这类日常事件
     *       每次都会写一整串 ERROR 堆栈，与方案 9.1「生产环境关闭详细异常堆栈、
     *       日志不得打印密码和敏感配置」的要求相悖。</li>
     * </ol>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException e) {
        log.debug("静态资源不存在: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ErrorCode.NOT_FOUND, "资源不存在: " + e.getResourcePath()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.ok(ApiResponse.fail(ErrorCode.DATA_CONFLICT,
                "数据冲突，可能违反唯一约束：" + rootMessage(e)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.SYSTEM_ERROR, "系统内部错误: " + rootMessage(e)));
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = t.getMessage();
        if (m == null) {
            return t.getClass().getSimpleName();
        }
        return m.length() > 400 ? m.substring(0, 400) : m;
    }
}
