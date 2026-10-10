package com.ees.repository;

import com.ees.domain.EvaluationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EvaluationResultRepository extends JpaRepository<EvaluationResult, Long> {

    List<EvaluationResult> findByTaskIdOrderByTotalScoreDesc(Long taskId);

    Optional<EvaluationResult> findById(Long id);

    Optional<EvaluationResult> findFirstByTaskIdOrderByTotalScoreDesc(Long taskId);

    long countByTaskId(Long taskId);

    long countByProjectId(Long projectId);

    long countByProjectIdAndPublishStatus(Long projectId, String publishStatus);

    List<EvaluationResult> findTop10ByProjectIdOrderByIdDesc(Long projectId);

    List<EvaluationResult> findByProjectIdOrderByIdAsc(Long projectId);

    @Query("""
            select r from EvaluationResult r
            where (:projectId is null or r.projectId = :projectId)
              and (:ids is null or r.projectId in :ids)
              and (:taskId is null or r.taskId = :taskId)
              and (:algorithm is null or r.algorithm = :algorithm)
              and (:level is null or r.level = :level)
              and (:publishStatus is null or r.publishStatus = :publishStatus)
              and (:objectCode is null or r.objectCode like %:objectCode%)
            """)
    Page<EvaluationResult> search(@Param("projectId") Long projectId,
                                  @Param("ids") Collection<Long> ids,
                                  @Param("taskId") Long taskId,
                                  @Param("algorithm") String algorithm,
                                  @Param("level") String level,
                                  @Param("publishStatus") String publishStatus,
                                  @Param("objectCode") String objectCode,
                                  Pageable pageable);

    @Query("select avg(r.totalScore) from EvaluationResult r where r.projectId = :projectId")
    BigDecimal avgScoreByProject(@Param("projectId") Long projectId);

    /**
     * 全部项目的平均分。
     *
     * <p>驾驶舱的「全部项目」视图原先没有这个查询，`avgScore` 被写死为 null，
     * 导致「平均得分」卡片恒显示 “-”；补上这条与 {@code avgScoreByProject} 对称的查询。</p>
     */
    @Query("select avg(r.totalScore) from EvaluationResult r")
    BigDecimal avgScoreAll();

    /** 按发布状态统计全部项目的结果数（「全部项目」视图的已发布数，原先被写死为 0）。 */
    long countByPublishStatus(String publishStatus);

    // ---- 数据权限过滤：只统计「可见项目集合」内的数据。管理员（ALL 范围）不走这些方法。 ----

    /** 可见项目集合内的结果数。 */
    long countByProjectIdIn(Collection<Long> projectIds);

    /** 可见项目集合内按发布状态的结果数。 */
    long countByProjectIdInAndPublishStatus(Collection<Long> projectIds, String publishStatus);

    /** 可见项目集合内的平均分。 */
    @Query("select avg(r.totalScore) from EvaluationResult r where r.projectId in :projectIds")
    BigDecimal avgScoreByProjects(@Param("projectIds") Collection<Long> projectIds);

    /**
     * 指定时间区间（按结果创建时间，左闭右开）内的等级分布。
     *
     * <p>驾驶舱的「评判等级分布」饼图与「任务趋势」折线图**共用同一统计区间**，
     * 由前端传 from/to 决定，默认近 7 天。{@code projectIds} 为 null 表示不限制
     * （管理员 / ALL 范围），否则只统计可见项目；空集合由服务层短路，不进来。</p>
     */
    @Query("""
            select r.level, count(r) from EvaluationResult r
            where r.createdAt >= :from and r.createdAt < :to
              and (:projectIds is null or r.projectId in :projectIds)
            group by r.level
            """)
    List<Object[]> countGroupByLevelInRange(@Param("projectIds") Collection<Long> projectIds,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    /** 可见项目集合内的算法使用统计。 */
    @Query("""
            select r.algorithm, r.algorithmVersion, count(r) from EvaluationResult r
            where r.projectId in :projectIds
            group by r.algorithm, r.algorithmVersion
            """)
    List<Object[]> countGroupByAlgorithmIn(@Param("projectIds") Collection<Long> projectIds);

    /** 可见项目集合内最新的 10 条结果。 */
    List<EvaluationResult> findTop10ByProjectIdInOrderByIdDesc(Collection<Long> projectIds);

    @Query("""
            select r.algorithm, r.algorithmVersion, count(r) from EvaluationResult r
            group by r.algorithm, r.algorithmVersion
            """)
    List<Object[]> countGroupByAlgorithm();
}
