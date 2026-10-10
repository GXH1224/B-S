package com.ees.repository;

import com.ees.domain.ReportRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportRecordRepository extends JpaRepository<ReportRecord, Long> {

    Optional<ReportRecord> findByReportCode(String reportCode);

    List<ReportRecord> findByTaskIdOrderByIdDesc(Long taskId);

    @Query("""
            select r from ReportRecord r
            where (:projectId is null or r.projectId = :projectId)
              and (:reportType is null or r.reportType = :reportType)
              and (:taskId is null or r.taskId = :taskId)
            """)
    Page<ReportRecord> search(@Param("projectId") Long projectId,
                              @Param("reportType") String reportType,
                              @Param("taskId") Long taskId,
                              Pageable pageable);
}
