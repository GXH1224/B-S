package com.ees.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 快照指标值（标准化后）。 */
@Entity
@Table(name = "snapshot_value")
@Getter
@Setter
public class SnapshotValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Column(name = "object_code", nullable = false, length = 64)
    private String objectCode;

    @Column(name = "object_name", length = 200)
    private String objectName;

    @Column(name = "data_time")
    private LocalDateTime dataTime;

    @Column(name = "indicator_code", nullable = false, length = 64)
    private String indicatorCode;

    @Column(name = "indicator_name", length = 200)
    private String indicatorName;

    @Column(name = "raw_value", precision = 24, scale = 8)
    private BigDecimal rawValue;

    @Column(name = "normalized_value", precision = 18, scale = 8)
    private BigDecimal normalizedValue;

    @Column(name = "normalized", nullable = false)
    private Boolean normalized = Boolean.FALSE;

    @Column(name = "valid", nullable = false)
    private Boolean valid = Boolean.TRUE;

    @Column(name = "outlier", nullable = false)
    private Boolean outlier = Boolean.FALSE;

    @Column(name = "missing", nullable = false)
    private Boolean missing = Boolean.FALSE;

    @Column(name = "remark", length = 500)
    private String remark;
}
