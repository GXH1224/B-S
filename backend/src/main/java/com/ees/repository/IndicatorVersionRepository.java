package com.ees.repository;

import com.ees.domain.IndicatorVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IndicatorVersionRepository extends JpaRepository<IndicatorVersion, Long> {

    Optional<IndicatorVersion> findByVersionCodeAndDeletedFalse(String versionCode);

    Optional<IndicatorVersion> findByIdAndDeletedFalse(Long id);

    boolean existsByVersionCode(String versionCode);

    List<IndicatorVersion> findByDeletedFalseOrderByIdDesc();

    @Query("""
            select v from IndicatorVersion v where v.deleted = false
              and (:keyword is null or v.versionName like %:keyword% or v.versionCode like %:keyword%)
              and (:status is null or v.status = :status)
              and (:projectId is null or v.projectId is null or v.projectId = :projectId)
              and (:ids is null or v.projectId is null or v.projectId in :ids)
            """)
    Page<IndicatorVersion> search(@Param("keyword") String keyword,
                                  @Param("status") String status,
                                  @Param("projectId") Long projectId,
                                  @Param("ids") Collection<Long> ids,
                                  Pageable pageable);

    long countByStatusAndDeletedFalse(String status);

    /**
     * 可见范围内的指定状态版本数（数据权限过滤用）。
     *
     * <p>与 {@code search} 同一口径：{@code projectId} 为空的**公共版本**对所有项目可见，因此也算在内。
     * 管理员（ALL 范围）走上面的 {@link #countByStatusAndDeletedFalse(String)}。</p>
     */
    @Query("""
            select count(v) from IndicatorVersion v
            where v.deleted = false and v.status = :status
              and (v.projectId is null or v.projectId in :projectIds)
            """)
    long countVisibleByStatus(@Param("status") String status, @Param("projectIds") Collection<Long> projectIds);

    /** 仅公共版本（{@code projectId} 为空）的指定状态数量：可见项目集合为空时仍应看到公共版本。 */
    long countByProjectIdIsNullAndStatusAndDeletedFalse(String status);
}
