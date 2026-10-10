package com.ees.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 指标定义。 */
@Entity
@Table(name = "indicator")
@Getter
@Setter
public class Indicator extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "indicator_code", nullable = false, length = 64)
    private String indicatorCode;

    @Column(name = "indicator_name", nullable = false, length = 200)
    private String indicatorName;

    @Column(name = "parent_code", length = 64)
    private String parentCode;

    @Column(name = "level", nullable = false)
    private Integer level = 1;

    @Column(name = "level_path", length = 1000)
    private String levelPath;

    @Column(name = "is_leaf", nullable = false)
    private Boolean leaf = Boolean.TRUE;

    @Column(name = "unit", length = 32)
    private String unit;

    /** POSITIVE / NEGATIVE / INTERVAL */
    @Column(name = "direction", nullable = false, length = 20)
    private String direction = "POSITIVE";

    @Column(name = "weight", precision = 18, scale = 8)
    private BigDecimal weight;

    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType = "NUMBER";

    @Column(name = "min_value", precision = 24, scale = 8)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 24, scale = 8)
    private BigDecimal maxValue;

    @Column(name = "interval_lower", precision = 24, scale = 8)
    private BigDecimal intervalLower;

    @Column(name = "interval_upper", precision = 24, scale = 8)
    private BigDecimal intervalUpper;

    @Column(name = "optimal_value", precision = 24, scale = 8)
    private BigDecimal optimalValue;

    @Column(name = "score_rule", columnDefinition = "TEXT")
    private String scoreRule;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "remark", length = 500)
    private String remark;
}
