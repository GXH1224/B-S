package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 评估数据快照（生成后不可变）。 */
@Entity
@Table(name = "data_snapshot")
@Getter
@Setter
public class DataSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_code", nullable = false, length = 64, unique = true)
    private String snapshotCode;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_code", length = 64)
    private String projectCode;

    @Column(name = "indicator_version_id")
    private Long indicatorVersionId;

    @Column(name = "indicator_version_code", length = 64)
    private String indicatorVersionCode;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "batch_code", length = 64)
    private String batchCode;

    @Column(name = "record_count", nullable = false)
    private Integer recordCount = 0;

    @Column(name = "object_count", nullable = false)
    private Integer objectCount = 0;

    @Column(name = "indicator_count", nullable = false)
    private Integer indicatorCount = 0;

    @Column(name = "missing_count", nullable = false)
    private Integer missingCount = 0;

    @Column(name = "outlier_count", nullable = false)
    private Integer outlierCount = 0;

    @Column(name = "checksum", length = 128)
    private String checksum;

    /** GENERATED / USED / INVALID */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "GENERATED";

    @Column(name = "storage_path", length = 1000)
    private String storagePath;

    @Column(name = "quality_report", columnDefinition = "TEXT")
    private String qualityReport;

    @Column(name = "data_range", length = 200)
    private String dataRange;

    @Column(name = "generated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "remark", length = 500)
    private String remark;
}
