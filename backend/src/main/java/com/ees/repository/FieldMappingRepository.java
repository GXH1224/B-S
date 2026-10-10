package com.ees.repository;

import com.ees.domain.FieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FieldMappingRepository extends JpaRepository<FieldMapping, Long> {

    List<FieldMapping> findByProjectId(Long projectId);

    List<FieldMapping> findByBatchId(Long batchId);

    void deleteByBatchId(Long batchId);
}
