package com.ees.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 系统参数。 */
@Entity
@Table(name = "sys_config")
@Getter
@Setter
public class SysConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, length = 128, unique = true)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "config_group", length = 50)
    private String configGroup = "SYSTEM";

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "editable", nullable = false)
    private Boolean editable = Boolean.TRUE;
}
