package com.ees.repository;

import com.ees.domain.SysConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysConfigRepository extends JpaRepository<SysConfig, Long> {

    Optional<SysConfig> findByConfigKey(String configKey);

    List<SysConfig> findAllByOrderByConfigGroupAscIdAsc();

    List<SysConfig> findByConfigGroupOrderByIdAsc(String configGroup);
}
