package com.ees.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 评估结果（每个被评估对象一条）。 */
@Entity
@Table(name = "evaluation_result")
@Getter
@Setter
public class EvaluationResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "task_code", length = 64)
    private String taskCode;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_code", length = 64)
    private String projectCode;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "object_code", nullable = false, length = 64)
    private String objectCode;

    @Column(name = "object_name", length = 200)
    private String objectName;

    @Column(name = "indicator_version_code", length = 64)
    private String indicatorVersionCode;

    @Column(name = "snapshot_code", length = 64)
    private String snapshotCode;

    @Column(name = "algorithm", length = 30)
    private String algorithm;

    @Column(name = "algorithm_version", length = 50)
    private String algorithmVersion;

    @Column(name = "total_score", precision = 18, scale = 6)
    private BigDecimal totalScore;

    @Column(name = "level", length = 50)
    private String level;

    @Column(name = "vector", columnDefinition = "TEXT")
    private String vector;

    @Column(name = "weights", columnDefinition = "TEXT")
    private String weights;

    @Column(name = "contributions", columnDefinition = "TEXT")
    private String contributions;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "warnings", columnDefinition = "TEXT")
    private String warnings;

    @Column(name = "extra", columnDefinition = "TEXT")
    private String extra;

    @Column(name = "ranking")
    private Integer ranking;

    /** NONE / PENDING / APPROVED / REJECTED */
    @Column(name = "reviewed", nullable = false, length = 20)
    private String reviewed = "NONE";

    @Column(name = "review_comment", length = 1000)
    private String reviewComment;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private java.time.LocalDateTime reviewedAt;

    /** DRAFT / PUBLISHED / ARCHIVED */
    @Column(name = "publish_status", nullable = false, length = 20)
    private String publishStatus = "DRAFT";

    @Column(name = "published_at")
    private java.time.LocalDateTime publishedAt;

    @Column(name = "published_by", length = 64)
    private String publishedBy;
}
