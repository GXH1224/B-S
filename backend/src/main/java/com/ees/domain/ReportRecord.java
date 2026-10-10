package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 报告 / 导出记录。 */
@Entity
@Table(name = "report_record")
@Getter
@Setter
public class ReportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_code", nullable = false, length = 64, unique = true)
    private String reportCode;

    @Column(name = "report_name", length = 300)
    private String reportName;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "result_id")
    private Long resultId;

    @Column(name = "project_id")
    private Long projectId;

    /** EXCEL / WORD / PDF / JSON */
    @Column(name = "report_type", nullable = false, length = 20)
    private String reportType;

    @Column(name = "file_name", length = 300)
    private String fileName;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    /** GENERATING / READY / FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "GENERATING";

    @Column(name = "error_msg", length = 1000)
    private String errorMsg;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now();
}
