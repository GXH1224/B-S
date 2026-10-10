package com.ees.repository;

import com.ees.domain.SysAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    long deleteByCreatedAtBefore(LocalDateTime time);
}
