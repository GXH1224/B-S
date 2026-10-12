package com.ees.repository;

import com.ees.domain.SysAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface SysAuditLogRepository extends JpaRepository<SysAuditLog, Long> {

    @Query("""
            select a from SysAuditLog a
            where (:username is null or a.username like %:username%)
              and (:module is null or a.module = :module)
              and (:success is null or a.success = :success)
              and (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt <= :to)
            """)
    Page<SysAuditLog> search(@Param("username") String username,
                            @Param("module") String module,
                            @Param("success") Boolean success,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            Pageable pageable);

    /**
     * 分批删除过期审计日志。
     *
     * <p>派生删除方法 {@code deleteByCreatedAtBefore} 是「先查后删」：会把待删实体
     * 全部加载进内存再逐条 remove，而该表 {@code detail} 是 TEXT 类型，体量大；
     * 单事务无界删除几百万行还会长时间持有数据库连接、生成巨大 undo log 并锁大量行。
     * 因此清理走这条原生批量删除，配合外层循环实现「每批一个短事务」。</p>
     *
     * <p>{@code created_at} 上已有索引 {@code idx_audit_time}，范围删除可走索引。</p>
     */
    @Modifying
    @Query(value = "DELETE FROM sys_audit_log WHERE created_at < :before LIMIT :batch", nativeQuery = true)
    int deleteBatchBefore(@Param("before") LocalDateTime before, @Param("batch") int batch);
}
