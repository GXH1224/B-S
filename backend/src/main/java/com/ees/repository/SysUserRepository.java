package com.ees.repository;

import com.ees.domain.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsernameAndDeletedFalse(String username);

    Optional<SysUser> findByIdAndDeletedFalse(Long id);

    boolean existsByUsernameAndDeletedFalse(String username);

    long countByRoleCodeAndDeletedFalse(String roleCode);

    List<SysUser> findAllByDeletedFalseOrderByIdAsc();

    @Query("""
            select u from SysUser u where u.deleted = false
              and (:keyword is null or u.username like %:keyword% or u.realName like %:keyword% or u.orgName like %:keyword%)
              and (:roleCode is null or u.roleCode = :roleCode)
              and (:status is null or u.status = :status)
            """)
    Page<SysUser> search(@Param("keyword") String keyword,
                         @Param("roleCode") String roleCode,
                         @Param("status") String status,
                         Pageable pageable);

    long countByStatusAndDeletedFalse(String status);
}
