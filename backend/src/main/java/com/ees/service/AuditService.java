package com.ees.service;

import com.ees.common.PageResult;
import com.ees.domain.SysAuditLog;
import com.ees.repository.SysAuditLogRepository;
import com.ees.security.LoginUser;
import com.ees.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 审计服务（方案 9.2）。导出、发布、数据源测试、权限变更和算法参数变更全部纳入审计，
 * 日志不得由普通用户删除。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final SysAuditLogRepository repository;

    public AuditService(SysAuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * 异步记录审计日志（写操作自动登记）。
     *
     * <p>@Async 线程不继承 SecurityContext，且 ServletRequest 在响应结束后会被回收，
     * 因此调用方必须在发起线程中解析好用户身份与请求元信息后以值对象传入。</p>
     */
    @Async("ioExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String module, String action, String targetType, String targetId,
                       String detail, boolean success, String errorMsg, Long costMs,
                       Long userId, String username, RequestInfo info) {
        try {
            SysAuditLog entry = new SysAuditLog();
            entry.setUserId(userId);
            entry.setUsername(username == null ? "anonymous" : username);
            entry.setModule(module);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail);
            entry.setSuccess(success);
            entry.setErrorMsg(truncate(errorMsg, 1900));
            entry.setCostMs(costMs);
            if (info != null) {
                entry.setHttpMethod(info.method);
                entry.setRequestUri(truncate(info.uri, 480));
                entry.setIp(info.ip);
                entry.setUserAgent(truncate(info.userAgent, 480));
            }
            entry.setCreatedAt(LocalDateTime.now());
            repository.save(entry);
        } catch (Exception e) {
            log.warn("写入审计日志失败: {}", e.getMessage());
        }
    }

    /** 请求元信息快照（在发起线程中采集）。 */
    public static class RequestInfo {
        public String method;
        public String uri;
        public String ip;
        public String userAgent;

        public static RequestInfo of(HttpServletRequest request) {
            if (request == null) {
                return null;
            }
            RequestInfo i = new RequestInfo();
            i.method = request.getMethod();
            i.uri = request.getRequestURI();
            i.ip = clientIp(request);
            i.userAgent = request.getHeader("User-Agent");
            return i;
        }
    }

    /** 同步记录（登录等关键操作）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSync(String module, String action, String targetType, String targetId,
                           String detail, boolean success, String errorMsg,
                           Long userId, String username, HttpServletRequest request) {        try {
            SysAuditLog entry = new SysAuditLog();
            entry.setUserId(userId);
            entry.setUsername(username == null ? "anonymous" : username);
            entry.setModule(module);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail);
            entry.setSuccess(success);
            entry.setErrorMsg(truncate(errorMsg, 1900));
            if (request != null) {
                entry.setHttpMethod(request.getMethod());
                entry.setRequestUri(truncate(request.getRequestURI(), 480));
                entry.setIp(clientIp(request));
                entry.setUserAgent(truncate(request.getHeader("User-Agent"), 480));
            }
            entry.setCreatedAt(LocalDateTime.now());
            repository.save(entry);
        } catch (Exception e) {
            log.warn("写入审计日志失败: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResult<SysAuditLog> search(String username, String module, Boolean success,
                                          LocalDateTime from, LocalDateTime to,
                                          int page, int size) {
        Page<SysAuditLog> p = repository.search(
                blankToNull(username), blankToNull(module), success, from, to,
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public SysAuditLog get(Long id) {
        return repository.findById(id).orElse(null);
    }

    /** 清理过期审计日志（仅系统管理员，且日志不得由普通用户删除）。 */
    @Transactional
    public int purge(int retentionDays) {
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        long n = repository.deleteByCreatedAtBefore(before);
        log.info("清理 {} 天前的审计日志，共 {} 条", retentionDays, n);
        return (int) n;
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String h : headers) {
            String v = request.getHeader(h);
            if (v != null && !v.isBlank() && !"unknown".equalsIgnoreCase(v)) {
                int idx = v.indexOf(',');
                return idx > 0 ? v.substring(0, idx).trim() : v.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
