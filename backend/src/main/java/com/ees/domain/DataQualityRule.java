package com.ees.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 数据质量规则。 */
@Entity
@Table(name = "data_quality_rule")
@Getter
@Setter
public class DataQualityRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "rule_code", nullable = false, length = 64, unique = true)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    /** REQUIRED / RANGE / FORMAT / DIRECTION / DUPLICATE / MISSING / UNIT */
    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType;

    @Column(name = "target_field", length = 200)
    private String targetField;

    @Column(name = "indicator_code", length = 64)
    private String indicatorCode;

    @Column(name = "expression", length = 500)
    private String expression;

    /** BLOCK / WARN / INFO */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "WARN";

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "description", length = 500)
    private String description;
}
