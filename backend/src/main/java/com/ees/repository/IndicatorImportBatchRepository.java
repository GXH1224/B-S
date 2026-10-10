package com.ees.repository;

import com.ees.domain.IndicatorImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndicatorImportBatchRepository extends JpaRepository<IndicatorImportBatch, Long> {

    List<IndicatorImportBatch> findByVersionIdOrderByIdDesc(Long versionId);

    List<IndicatorImportBatch> findTop100ByOrderByIdDesc();
}
