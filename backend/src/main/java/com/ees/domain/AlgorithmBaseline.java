package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 算法基准样例（用于标准样例回归验证）。 */
@Entity
@Table(name = "algorithm_baseline")
@Getter
@Setter
public class AlgorithmBaseline extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "baseline_code", nullable = false, length = 64, unique = true)
    private String baselineCode;

    @Column(name = "baseline_name", nullable = false, length = 200)
    private String baselineName;

    /** AHP / FUZZY / WEIGHTED */
    @Column(name = "algorithm", nullable = false, length = 30)
    private String algorithm;

    @Column(name = "input_payload", columnDefinition = "TEXT")
    private String inputPayload;

    @Column(name = "expected_payload", columnDefinition = "TEXT")
    private String expectedPayload;

    @Column(name = "abs_tolerance", precision = 18, scale = 8)
    private BigDecimal absTolerance;

    @Column(name = "rel_tolerance", precision = 18, scale = 8)
    private BigDecimal relTolerance;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "last_run_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastRunAt;

    @Column(name = "last_run_result", length = 2000)
    private String lastRunResult;
}
