package com.ees.repository;

import com.ees.domain.EvaluationTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EvaluationTaskRepository extends JpaRepository<EvaluationTask, Long> {

    Optional<EvaluationTask> findByTaskCodeAndDeletedFalse(String taskCode);

    Optional<EvaluationTask> findByIdAndDeletedFalse(Long id);

    List<EvaluationTask> findTop20ByDeletedFalseOrderByIdDesc();

    List<EvaluationTask> findByProjectIdAndDeletedFalseOrderByIdDesc(Long projectId);

    List<EvaluationTask> findByStatusIn(List<String> statuses);

    long countByStatusAndDeletedFalse(String status);

    long countByAlgorithmAndDeletedFalse(String algorithm);

    long countByCreatedAtAfterAndDeletedFalse(LocalDateTime time);

    // ---- 数据权限过滤：只统计「可见项目集合」内的数据。管理员（ALL 范围）不走这些方法，直接用上面的全量计数。 ----

    long countByProjectIdInAndDeletedFalse(Collection<Long> projectIds);

    long countByProjectIdInAndStatusAndDeletedFalse(Collection<Long> projectIds, String status);

    long countByProjectIdInAndCreatedAtAfterAndDeletedFalse(Collection<Long> projectIds, LocalDateTime time);

    List<EvaluationTask> findTop20ByProjectIdInAndDeletedFalseOrderByIdDesc(Collection<Long> projectIds);

    /**
     * 指定时间区间（按创建时间，左闭右开）内的任务，供驾驶舱「任务趋势」按天分桶。
     *
     * <p>{@code ids} 为 null 表示不限制（管理员 / ALL 范围），否则只取可见项目；空集合由服务层短路。</p>
     */
    @Query("""
            select t from EvaluationTask t
            where t.deleted = false
              and t.createdAt >= :from and t.createdAt < :to
              and (:ids is null or t.projectId in :ids)
            """)
    List<EvaluationTask> findForTrend(@Param("ids") Collection<Long> ids,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    @Query("""
            select t from EvaluationTask t where t.deleted = false
              and (:projectId is null or t.projectId = :projectId)
              and (:ids is null or t.projectId in :ids)
              and (:algorithm is null or t.algorithm = :algorithm)
              and (:status is null or t.status = :status)
              and (:keyword is null or t.taskCode like %:keyword% or t.taskName like %:keyword%)
              and (:from is null or t.createdAt >= :from)
              and (:to is null or t.createdAt <= :to)
            """)
    Page<EvaluationTask> search(@Param("projectId") Long projectId,
                                @Param("ids") Collection<Long> ids,
                                @Param("algorithm") String algorithm,
                                @Param("status") String status,
                                @Param("keyword") String keyword,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                Pageable pageable);
}
