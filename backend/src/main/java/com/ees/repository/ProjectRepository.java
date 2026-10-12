package com.ees.repository;

import com.ees.domain.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByProjectCodeAndDeletedFalse(String projectCode);

    Optional<Project> findByIdAndDeletedFalse(Long id);

    boolean existsByProjectCodeAndDeletedFalse(String projectCode);

    List<Project> findAllByDeletedFalseOrderByIdDesc();

    /**
     * 带分页上限的项目列表查询。
     *
     * <p>供无分页的 {@code GET /api/projects/all} 使用：原实现走
     * {@code findAllByDeletedFalseOrderByIdDesc()} 会把整表拉进内存，
     * 既无鉴权也无条数上限。</p>
     */
    Page<Project> findAllByDeletedFalse(Pageable pageable);

    @Query("""
            select p from Project p where p.deleted = false
              and (:keyword is null or p.projectName like %:keyword% or p.projectCode like %:keyword%)
              and (:status is null or p.status = :status)
              and (:ids is null or p.id in :ids)
            """)
    Page<Project> search(@Param("keyword") String keyword,
                         @Param("status") String status,
                         @Param("ids") List<Long> ids,
                         Pageable pageable);

    long countByStatusAndDeletedFalse(String status);

    /** 可见项目集合内按状态计数（数据权限过滤用；管理员用上面的全量计数）。 */
    long countByStatusAndDeletedFalseAndIdIn(String status, Collection<Long> ids);
}
