package com.ees.repository;

import com.ees.domain.AhpConsistency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AhpConsistencyRepository extends JpaRepository<AhpConsistency, Long> {

    List<AhpConsistency> findByTaskIdOrderByIdAsc(Long taskId);

    void deleteByTaskId(Long taskId);
}
