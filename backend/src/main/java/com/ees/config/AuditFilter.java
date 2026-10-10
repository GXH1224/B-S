package com.ees.config;

import com.ees.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 审计过滤器：对写操作（POST/PUT/DELETE/PATCH）自动登记审计日志。
 */
@Component
@Order(20)
public class AuditFilter extends OncePerRequestFilter {

    private final EesProperties properties;
    private final AuditService auditService;

    public AuditFilter(EesProperties properties, AuditService auditService) {
        this.properties = properties;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        List<String> methods = properties.getAudit().methodList();
        boolean auditable = properties.getAudit().isEnabled()
                && methods.contains(request.getMethod().toUpperCase())
                && !request.getRequestURI().startsWith("/api/audit");

        if (!auditable) {
            chain.doFilter(request, response);
            return;
        }

        long begin = System.currentTimeMillis();
        // 在发起线程采集用户身份与请求元信息：异步记录无法访问 SecurityContext 与已回收的 request
        Long userId = com.ees.security.SecurityUtils.currentUserId();
        String username = com.ees.security.SecurityUtils.currentUsername();
        com.ees.service.AuditService.RequestInfo info =
                com.ees.service.AuditService.RequestInfo.of(request);
        try {
            chain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - begin;
            String module = resolveModule(info == null ? null : info.uri);
            String action = (info == null ? "" : info.method + " " + info.uri);
            boolean ok = response.getStatus() < 400;
            String err = ok ? null : ("HTTP " + response.getStatus());
            auditService.record(module, action, null, null, null, ok, err, cost,
                    userId, username, info);
        }
    }

    /**
     * 按方案 9.2 把操作归类到 登录 / 数据 / 任务 / 配置 / 导出 五类审计。
     * 注意：接口路径形如 /api/evaluation-results/1/exports，因此不能用 "/results" 这种子串做判断。
     */
    private String resolveModule(String uri) {
        if (uri == null) {
            return "SYSTEM";
        }
        if (uri.contains("/auth/")) {
            return "AUTH";
        }
        // 导出优先：报告生成/下载与结果导出
        if (uri.contains("export") || uri.contains("/reports")) {
            return "EXPORT";
        }
        // 评估任务
        if (uri.contains("/evaluation-tasks") || uri.contains("/evaluation-results")) {
            return "TASK";
        }
        // 数据审计：数据源、文件导入、抽取批次、校验、快照、指标体系
        if (uri.contains("/data-sources") || uri.contains("/data-batches") || uri.contains("/snapshots")
                || uri.contains("/indicator-versions") || uri.contains("/indicators")) {
            return "DATA";
        }
        // 配置审计：用户、角色、系统参数、质量规则
        if (uri.contains("/users") || uri.contains("/roles") || uri.contains("/permissions")
                || uri.contains("/configs") || uri.contains("/quality-rules")) {
            return "CONFIG";
        }
        return "SYSTEM";
    }
}
