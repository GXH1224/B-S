package com.ees.service;

import com.ees.repository.SysAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 审计日志的短事务辅助服务。
 *
 * <p>单独一个 Bean 的原因：Spring {@code @Transactional} 基于代理，
 * 同类内部自调用不生效。{@link AuditService#purge(int)} 需要「每批一个独立事务」，
 * 因此把单批删除放到本类，由外部调用触发事务代理。</p>
 */
@Service
public class AuditTxService {

    private final SysAuditLogRepository repository;

    public AuditTxService(SysAuditLogRepository repository) {
        this.repository = repository;
    }

    /** 删除一批过期日志，返回实际删除行数；每批一个独立短事务，批间连接即释放。 */
    @Transactional
    public int deleteBatch(LocalDateTime before, int batch) {
        return repository.deleteBatchBefore(before, batch);
    }
}
