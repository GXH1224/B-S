package com.ees.repository;

import com.ees.domain.DataBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DataBatchRepository extends JpaRepository<DataBatch, Long> {

    Optional<DataBatch> findByBatchCodeAndDeletedFalse(String batchCode);

    Optional<DataBatch> findByIdAndDeletedFalse(Long id);

    List<DataBatch> findByProjectIdAndDeletedFalseOrderByIdDesc(Long projectId);

    @Query("""
            select b from DataBatch b where b.deleted = false
              and (:projectId is null or b.projectId = :projectId)
              and (:ids is null or b.projectId in :ids)
              and (:sourceType is null or b.sourceType = :sourceType)
              and (:status is null or b.status = :status)
              and (:keyword is null or b.batchCode like %:keyword% or b.fileName like %:keyword%)
            """)
    Page<DataBatch> search(@Param("projectId") Long projectId,
                           @Param("ids") Collection<Long> ids,
                           @Param("sourceType") String sourceType,
                           @Param("status") String status,
                           @Param("keyword") String keyword,
                           Pageable pageable);

    long countByProjectIdAndDeletedFalse(Long projectId);

    /** 可见项目集合内的批次总数（数据权限过滤用）。 */
    long countByProjectIdInAndDeletedFalse(Collection<Long> projectIds);
}
