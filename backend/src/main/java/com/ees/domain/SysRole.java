package com.ees.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 角色（系统内置角色字典）。
 *
 * <p>对应方案 4.1 的角色字典，但系统已精简为两类角色：系统管理员（SYSTEM_ADMIN，data_scope=ALL）
 * 与评估人员（EVALUATOR，data_scope=PROJECT）；原数据管理员 / 审核人员 / 领导查看人员三类已删除。
 * 权威来源见 {@code AuthService.menusOf()} 与 {@code db/data.sql} 中 sys_role 的两条 INSERT IGNORE。</p>
 */
@Entity
@Table(name = "sys_role")
public class SysRole extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false, length = 50, unique = true)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(name = "description", length = 500)
    private String description;

    /** 权限编码 JSON 数组 */
    @Column(name = "permissions", columnDefinition = "TEXT")
    private String permissions;

    /** ALL / PROJECT / SELF */
    @Column(name = "data_scope", length = 30)
    private String dataScope = "PROJECT";

    @Column(name = "built_in", nullable = false)
    private Boolean builtIn = Boolean.FALSE;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public String getDataScope() {
        return dataScope;
    }

    public void setDataScope(String dataScope) {
        this.dataScope = dataScope;
    }

    public Boolean getBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(Boolean builtIn) {
        this.builtIn = builtIn;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
