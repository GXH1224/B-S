package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 外部数据源（甲方业务库只读接入）。 */
@Entity
@Table(name = "data_source")
@Getter
@Setter
public class DataSourceEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_code", nullable = false, length = 64, unique = true)
    private String sourceCode;

    @Column(name = "source_name", nullable = false, length = 200)
    private String sourceName;

    /** MYSQL / POSTGRESQL / ORACLE / SQLSERVER / DM / KINGBASE / JDBC */
    @Column(name = "db_type", nullable = false, length = 30)
    private String dbType = "MYSQL";

    @Column(name = "jdbc_url", nullable = false, length = 1000)
    private String jdbcUrl;

    @Column(name = "driver_class", length = 255)
    private String driverClass;

    @Column(name = "db_username", nullable = false, length = 128)
    private String dbUsername;

    /** AES 加密保存，页面不回显完整密码 */
    @Column(name = "db_password", nullable = false, length = 1000)
    private String dbPassword;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "read_only", nullable = false)
    private Boolean readOnly = Boolean.TRUE;

    @Column(name = "max_rows", nullable = false)
    private Integer maxRows = 100000;

    /** UNKNOWN / OK / FAIL */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "UNKNOWN";

    @Column(name = "last_test_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastTestAt;

    @Column(name = "last_test_result", length = 2000)
    private String lastTestResult;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = Boolean.FALSE;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Transient
    public String getDbPasswordMasked() {
        return "******";
    }
}
