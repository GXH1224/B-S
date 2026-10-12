package com.ees.config;

import com.ees.service.AuditService;
import com.ees.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审计日志定时清理任务。
 *
 * <p>背景：{@code sys_audit_log} 原本没有任何自动清理机制，200 用户规模下
 * 每天约新增 4,000~10,000 条，一年累计 150 万~360 万条，表会无限膨胀，
 * 进而拖慢 {@code GET /api/audit-logs} 这类大表分页查询。</p>
 *
 * <p>{@code @EnableScheduling} 早已在 {@code EesApplication} 上开启，但此前
 * 全项目没有任何 {@code @Scheduled} 方法 —— 基础设施开了却没人用。本类补上这一环。</p>
 *
 * <p>保留天数复用已有配置项 {@code audit.retention.days}（缺省 365 天），无需新增配置。
 * 如需关闭自动清理，把保留天数配成一个很大的值即可。</p>
 */
@Component
public class AuditCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuditCleanupJob.class);

    /** 配置键：审计日志保留天数。 */
    private static final String KEY_RETENTION_DAYS = "audit.retention.days";

    /**
     * 配置缺失时的缺省保留天数：一个月。
     * 实际生效值还会被 {@link AuditService#clampRetentionDays(int)} 收敛到 [1, 30] 天，
     * 因此即使库里 {@code audit.retention.days} 仍是历史值 365，也只清理一个月前的日志。
     */
    private static final int DEFAULT_RETENTION_DAYS = AuditService.MAX_RETENTION_DAYS;

    private final AuditService auditService;
    private final ConfigService configService;

    public AuditCleanupJob(AuditService auditService, ConfigService configService) {
        this.auditService = auditService;
        this.configService = configService;
    }

    /** 每天凌晨 3:30 清理过期审计日志。 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanup() {
        int days = configService.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS);
        try {
            int removed = auditService.purge(days);
            log.info("审计日志定时清理完成：保留 {} 天，本次删除 {} 条", days, removed);
        } catch (Exception e) {
            // 清理失败不能影响主流程，也不能让调度线程因异常而中断后续调度
            log.warn("审计日志定时清理失败（保留 {} 天）: {}", days, e.getMessage());
        }
    }
}
