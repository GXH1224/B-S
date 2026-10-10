package com.ees.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 结果指标明细（行式，支撑表格分页、排序与导出）。 */
@Entity
@Table(name = "result_detail")
@Getter
@Setter
public class ResultDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "result_id", nullable = false)
    private Long resultId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "object_code", length = 64)
    private String objectCode;

    @Column(name = "indicator_code", nullable = false, length = 64)
    private String indicatorCode;

    @Column(name = "indicator_name", length = 200)
    private String indicatorName;

    @Column(name = "indicator_level")
    private Integer indicatorLevel = 1;

    @Column(name = "parent_code", length = 64)
    private String parentCode;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "direction", length = 20)
    private String direction;

    @Column(name = "raw_value", precision = 24, scale = 8)
    private BigDecimal rawValue;

    @Column(name = "normalized_value", precision = 18, scale = 8)
    private BigDecimal normalizedValue;

    @Column(name = "weight", precision = 18, scale = 8)
    private BigDecimal weight;

    @Column(name = "contribution", precision = 18, scale = 8)
    private BigDecimal contribution;

    @Column(name = "score", precision = 18, scale = 8)
    private BigDecimal score;

    @Column(name = "grade", length = 50)
    private String grade;

    @Column(name = "rank_no")
    private Integer rankNo;

    @Column(name = "remark", length = 500)
    private String remark;
}
