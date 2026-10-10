package com.ees.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 项目成员（项目级数据授权）。 */
@Entity
@Table(name = "project_member")
@Getter
@Setter
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 64)
    private String username;

    /** MANAGER / DATA / EVALUATOR / REVIEWER / VIEWER */
    @Column(name = "member_role", length = 50)
    private String memberRole = "VIEWER";

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now();
}
