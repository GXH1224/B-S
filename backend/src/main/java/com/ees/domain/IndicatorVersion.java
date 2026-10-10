package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 指标体系版本（原始版本只读，绑定版本号）。 */
@Entity
@Table(name = "indicator_version")
@Getter
@Setter
public class IndicatorVersion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_code", nullable = false, length = 64, unique = true)
    private String versionCode;

    @Column(name = "version_name", nullable = false, length = 200)
    private String versionName;

    @Column(name = "project_id")
    private Long projectId;

    /** FILE / MANUAL / API */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType = "FILE";

    @Column(name = "source_file", length = 500)
    private String sourceFile;

    @Column(name = "source_hash", length = 128)
    private String sourceHash;

    @Column(name = "import_batch_id")
    private Long importBatchId;

    @Column(name = "indicator_count", nullable = false)
    private Integer indicatorCount = 0;

    @Column(name = "leaf_count", nullable = false)
    private Integer leafCount = 0;

    @Column(name = "max_level", nullable = false)
    private Integer maxLevel = 0;

    /** DRAFT / VALIDATED / PUBLISHED / INVALID / ARCHIVED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "validate_result", columnDefinition = "TEXT")
    private String validateResult;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "published_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;

    @Column(name = "published_by", length = 64)
    private String publishedBy;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = Boolean.FALSE;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}
