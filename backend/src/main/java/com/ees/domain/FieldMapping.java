package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 字段映射。 */
@Entity
@Table(name = "field_mapping")
@Getter
@Setter
public class FieldMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "source_field", nullable = false, length = 200)
    private String sourceField;

    @Column(name = "target_field", nullable = false, length = 200)
    private String targetField;

    /** NONE / TRIM / UPPER / LOWER / NUMBER / DATE */
    @Column(name = "transform_rule", length = 200)
    private String transformRule = "NONE";

    @Column(name = "required", nullable = false)
    private Boolean required = Boolean.FALSE;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now();
}
