package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 数据源字段缓存（字段预览）。 */
@Entity
@Table(name = "data_source_field")
@Getter
@Setter
public class DataSourceField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(name = "table_name", length = 200)
    private String tableName;

    @Column(name = "column_name", nullable = false, length = 200)
    private String columnName;

    @Column(name = "data_type", length = 100)
    private String dataType;

    @Column(name = "column_comment", length = 500)
    private String columnComment;

    @Column(name = "nullable")
    private Boolean nullable = Boolean.TRUE;

    @Column(name = "ordinal_position")
    private Integer ordinalPosition = 0;

    @Column(name = "sample_value", length = 500)
    private String sampleValue;

    @Column(name = "cached_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cachedAt = LocalDateTime.now();
}
