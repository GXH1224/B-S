package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 指标体系导入批次。 */
@Entity
@Table(name = "indicator_import_batch")
@Getter
@Setter
public class IndicatorImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id")
    private Long versionId;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "stored_path", length = 1000)
    private String storedPath;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    @Column(name = "success_rows", nullable = false)
    private Integer successRows = 0;

    @Column(name = "error_rows", nullable = false)
    private Integer errorRows = 0;

    /** SUCCESS / PARTIAL / FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "SUCCESS";

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now();
}
