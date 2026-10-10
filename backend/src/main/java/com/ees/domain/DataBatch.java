package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 数据批次。 */
@Entity
@Table(name = "data_batch")
@Getter
@Setter
public class DataBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_code", nullable = false, length = 64, unique = true)
    private String batchCode;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_code", length = 64)
    private String projectCode;

    /** FILE / DATABASE / API / SFTP */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType = "FILE";

    @Column(name = "source_ref", length = 1000)
    private String sourceRef;

    @Column(name = "data_source_id")
    private Long dataSourceId;

    @Column(name = "query_sql", columnDefinition = "TEXT")
    private String querySql;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "stored_path", length = 1000)
    private String storedPath;

    @Column(name = "target_version_id")
    private Long targetVersionId;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    @Column(name = "valid_rows", nullable = false)
    private Integer validRows = 0;

    @Column(name = "error_rows", nullable = false)
    private Integer errorRows = 0;

    @Column(name = "object_count", nullable = false)
    private Integer objectCount = 0;

    @Column(name = "indicator_count", nullable = false)
    private Integer indicatorCount = 0;

    /** IMPORTED / VALIDATED / FAILED / SNAPSHOTTED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "IMPORTED";

    @Column(name = "quality_report", columnDefinition = "TEXT")
    private String qualityReport;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = Boolean.FALSE;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by", length = 64)
    private String createdBy;
}
