package com.ees.repository;

import com.ees.domain.AlgorithmBaseline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlgorithmBaselineRepository extends JpaRepository<AlgorithmBaseline, Long> {

    Optional<AlgorithmBaseline> findByBaselineCode(String baselineCode);

    List<AlgorithmBaseline> findByEnabledTrueOrderByIdAsc();

    List<AlgorithmBaseline> findByAlgorithmOrderByIdAsc(String algorithm);
}
