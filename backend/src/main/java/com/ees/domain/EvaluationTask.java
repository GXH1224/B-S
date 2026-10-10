package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 评估任务。 */
@Entity
@Table(name = "evaluation_task")
@Getter
@Setter
public class EvaluationTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_code", nullable = false, length = 64, unique = true)
    private String taskCode;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "project_code", length = 64)
    private String projectCode;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "indicator_version_id", nullable = false)
    private Long indicatorVersionId;

    @Column(name = "indicator_version_code", length = 64)
    private String indicatorVersionCode;

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Column(name = "snapshot_code", length = 64)
    private String snapshotCode;

    /** AHP / FUZZY / WEIGHTED / COMBO */
    @Column(name = "algorithm", nullable = false, length = 30)
    private String algorithm;

    @Column(name = "algorithm_version", length = 50)
    private String algorithmVersion;

    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    @Column(name = "combo_flow", columnDefinition = "TEXT")
    private String comboFlow;

    @Column(name = "input_summary", columnDefinition = "TEXT")
    private String inputSummary;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "progress", nullable = false)
    private Integer progress = 0;

    @Column(name = "current_step", length = 200)
    private String currentStep;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_msg", length = 2000)
    private String errorMsg;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "retry_of")
    private Long retryOf;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "submit_by", length = 64)
    private String submitBy;

    @Column(name = "submit_by_id")
    private Long submitById;

    @Column(name = "queued_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime queuedAt;

    @Column(name = "started_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "object_count", nullable = false)
    private Integer objectCount = 0;

    @Column(name = "result_count", nullable = false)
    private Integer resultCount = 0;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = Boolean.FALSE;
}
