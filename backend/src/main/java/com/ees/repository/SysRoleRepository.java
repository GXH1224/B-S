package com.ees.repository;

import com.ees.domain.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

    Optional<SysRole> findByRoleCode(String roleCode);

    List<SysRole> findAllByOrderBySortOrderAsc();

    boolean existsByRoleCode(String roleCode);
}
