package com.ees.repository;

import com.ees.domain.ResultDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResultDetailRepository extends JpaRepository<ResultDetail, Long> {

    List<ResultDetail> findByResultIdOrderByIndicatorCodeAsc(Long resultId);

    List<ResultDetail> findByTaskIdOrderByIdAsc(Long taskId);

    List<ResultDetail> findByTaskIdAndObjectCodeOrderByIndicatorCodeAsc(Long taskId, String objectCode);

    void deleteByResultId(Long resultId);

    void deleteByTaskId(Long taskId);

    @Query("""
            select d from ResultDetail d
            where d.resultId = :resultId
              and (:keyword is null or d.indicatorCode like %:keyword% or d.indicatorName like %:keyword%)
            """)
    Page<ResultDetail> searchByResult(@Param("resultId") Long resultId,
                                      @Param("keyword") String keyword,
                                      Pageable pageable);

    @Query("""
            select d.indicatorCode, d.indicatorName, avg(d.score), avg(d.contribution)
            from ResultDetail d where d.taskId = :taskId
            group by d.indicatorCode, d.indicatorName
            order by avg(d.score) desc
            """)
    List<Object[]> averageByIndicator(@Param("taskId") Long taskId);
}
