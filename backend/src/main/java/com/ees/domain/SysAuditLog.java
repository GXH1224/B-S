package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 审计日志（方案 9.2）。 */
@Entity
@Table(name = "sys_audit_log")
@Getter
@Setter
public class SysAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 64)
    private String username;

    /** AUTH / DATA / TASK / CONFIG / EXPORT / SYSTEM */
    @Column(name = "module", length = 50)
    private String module;

    @Column(name = "action", length = 64)
    private String action;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "request_uri", length = 500)
    private String requestUri;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "success", nullable = false)
    private Boolean success = Boolean.TRUE;

    @Column(name = "error_msg", length = 2000)
    private String errorMsg;

    @Column(name = "cost_ms")
    private Long costMs;

    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now();
}
