package com.ees.repository;

import com.ees.domain.DataQualityRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataQualityRuleRepository extends JpaRepository<DataQualityRule, Long> {

    List<DataQualityRule> findByEnabledTrueOrderByIdAsc();

    List<DataQualityRule> findByProjectIdIsNullOrProjectIdOrderByIdAsc(Long projectId);

    List<DataQualityRule> findByRuleTypeAndEnabledTrue(String ruleType);
}
