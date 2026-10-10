package com.ees.repository;

import com.ees.domain.DataSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DataSnapshotRepository extends JpaRepository<DataSnapshot, Long> {

    Optional<DataSnapshot> findBySnapshotCode(String snapshotCode);

    Optional<DataSnapshot> findById(Long id);

    List<DataSnapshot> findByProjectIdOrderByIdDesc(Long projectId);

    List<DataSnapshot> findByBatchId(Long batchId);

    @Query("""
            select s from DataSnapshot s
            where (:projectId is null or s.projectId = :projectId)
              and (:ids is null or s.projectId in :ids)
              and (:status is null or s.status = :status)
              and (:keyword is null or s.snapshotCode like %:keyword%)
            """)
    Page<DataSnapshot> search(@Param("projectId") Long projectId,
                              @Param("ids") Collection<Long> ids,
                              @Param("status") String status,
                              @Param("keyword") String keyword,
                              Pageable pageable);

    long countByProjectId(Long projectId);

    /** 可见项目集合内的快照总数（数据权限过滤用）。 */
    long countByProjectIdIn(Collection<Long> projectIds);
}
