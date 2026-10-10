package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AHP 一致性检验记录。 */
@Entity
@Table(name = "ahp_consistency")
@Getter
@Setter
public class AhpConsistency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "result_id")
    private Long resultId;

    @Column(name = "object_code", length = 64)
    private String objectCode;

    @Column(name = "node_code", length = 64)
    private String nodeCode;

    @Column(name = "node_name", length = 200)
    private String nodeName;

    @Column(name = "matrix_size")
    private Integer matrixSize;

    @Column(name = "matrix_data", columnDefinition = "TEXT")
    private String matrixData;

    @Column(name = "eigen_vector", columnDefinition = "TEXT")
    private String eigenVector;

    @Column(name = "lambda_max", precision = 18, scale = 8)
    private BigDecimal lambdaMax;

    @Column(name = "ci", precision = 18, scale = 8)
    private BigDecimal ci;

    @Column(name = "ri", precision = 18, scale = 8)
    private BigDecimal ri;

    @Column(name = "cr", precision = 18, scale = 8)
    private BigDecimal cr;

    @Column(name = "passed", nullable = false)
    private Boolean passed = Boolean.TRUE;

    @Column(name = "warning", length = 500)
    private String warning;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now();
}
