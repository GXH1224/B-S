package com.ees.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 原始数据记录（保存原始数据，标准化数据另存快照）。 */
@Entity
@Table(name = "raw_data_record")
@Getter
@Setter
public class RawDataRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "row_no")
    private Integer rowNo;

    @Column(name = "project_code", length = 64)
    private String projectCode;

    @Column(name = "object_code", length = 64)
    private String objectCode;

    @Column(name = "object_name", length = 200)
    private String objectName;

    @Column(name = "data_time")
    private LocalDateTime dataTime;

    @Column(name = "indicator_code", length = 64)
    private String indicatorCode;

    @Column(name = "indicator_name", length = 200)
    private String indicatorName;

    @Column(name = "indicator_value", precision = 24, scale = 8)
    private BigDecimal indicatorValue;

    @Column(name = "indicator_value_text", length = 200)
    private String indicatorValueText;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "source_batch", length = 64)
    private String sourceBatch;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "valid", nullable = false)
    private Boolean valid = Boolean.TRUE;

    @Column(name = "error_msg", length = 500)
    private String errorMsg;
}
