package com.ees.repository;

import com.ees.domain.TaskStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, Long> {

    List<TaskStatusHistory> findByTaskIdOrderByIdAsc(Long taskId);
}
