package com.ees.common;

/**
 * 统一错误码。对应方案 8.3 错误类别。
 */
public enum ErrorCode {

    // 通用 1xxx
    SUCCESS(0, "成功"),
    BUSINESS_ERROR(1000, "业务处理失败"),
    PARAM_INVALID(1001, "参数校验失败"),
    NOT_FOUND(1004, "资源不存在"),
    DATA_CONFLICT(1009, "数据冲突"),
    SYSTEM_ERROR(1500, "系统内部错误"),

    // 认证授权 2xxx
    UNAUTHORIZED(2001, "未登录或登录已过期"),
    FORBIDDEN(2003, "无权访问该资源"),
    LOGIN_FAILED(2004, "用户名或密码错误"),
    ACCOUNT_DISABLED(2005, "账号已停用"),
    ACCOUNT_LOCKED(2006, "账号已锁定，请稍后再试"),
    TOKEN_INVALID(2007, "登录凭证无效"),

    // 输入数据 3xxx（输入错误：阻止提交，返回字段级错误）
    INPUT_VALIDATE_FAILED(3001, "输入数据校验未通过"),
    FILE_TYPE_NOT_ALLOWED(3002, "文件类型不允许"),
    FILE_TOO_LARGE(3003, "文件超出大小限制"),
    FILE_PARSE_FAILED(3004, "文件解析失败"),
    INDICATOR_NOT_COVERED(3005, "数据未覆盖指标体系必需指标"),
    DATA_FIELD_MISSING(3006, "必填字段缺失"),
    DATA_FORMAT_INVALID(3007, "数值格式非法"),
    DATA_VALUE_OUT_OF_RANGE(3008, "指标值越界"),
    DIRECTION_UNKNOWN(3009, "指标正负方向未确认"),

    // 算法 4xxx（算法错误：任务失败并保存算法原始错误）
    ALGORITHM_ERROR(4001, "算法执行失败"),
    AHP_MATRIX_INVALID(4002, "判断矩阵不合法"),
    AHP_CR_NOT_PASSED(4003, "一致性检验未通过（CR 超限）"),
    ALGORITHM_PARAM_INVALID(4004, "算法参数非法"),
    ALGORITHM_NOT_SUPPORTED(4005, "不支持的评估算法"),
    MISSING_VALUE_BLOCKED(4006, "存在缺失值，按当前策略不允许计算"),

    // 运行错误 5xxx
    ALGORITHM_ENGINE_UNAVAILABLE(5001, "算法引擎不可用"),
    TASK_TIMEOUT(5002, "任务执行超时"),
    TASK_CANCELED(5003, "任务已取消"),
    TASK_STATUS_ILLEGAL(5004, "任务状态不允许该操作"),

    // 数据源 6xxx
    DATASOURCE_CONNECT_FAILED(6001, "数据源连接失败"),
    DATASOURCE_SQL_FORBIDDEN(6002, "SQL 含禁止的写操作关键字"),
    DATASOURCE_QUERY_FAILED(6003, "数据抽取失败"),
    DATASOURCE_NOT_FOUND(6004, "数据源不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
