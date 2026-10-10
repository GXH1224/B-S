package com.ees.repository;

import com.ees.domain.Indicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndicatorRepository extends JpaRepository<Indicator, Long> {

    List<Indicator> findByVersionIdOrderBySortOrderAscIdAsc(Long versionId);

    List<Indicator> findByVersionIdAndLeafTrueOrderBySortOrderAsc(Long versionId);

    List<Indicator> findByVersionIdAndLevelOrderBySortOrderAsc(Long versionId, Integer level);

    void deleteByVersionId(Long versionId);

    long countByVersionId(Long versionId);
}
