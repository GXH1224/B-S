package com.ees.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 登录用户上下文。权限集合包含 ROLE_xxx 与细粒度权限码。
 */
public class LoginUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String realName;
    private final String roleCode;
    private final String roleName;
    private final String dataScope;
    private final Set<String> permissions;
    private final boolean enabled;

    public LoginUser(Long userId, String username, String password, String realName,
                     String roleCode, String roleName, String dataScope,
                     Set<String> permissions, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.realName = realName;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.dataScope = dataScope == null ? "PROJECT" : dataScope;
        this.permissions = permissions == null ? Collections.emptySet() : permissions;
        this.enabled = enabled;
    }

    /** 仅用于令牌解析后的轻量上下文（不含密码）。 */
    public static LoginUser forToken(Long userId, String username, String realName,
                                     String roleCode, String dataScope, Set<String> permissions) {
        return new LoginUser(userId, username, null, realName, roleCode, null, dataScope, permissions, true);
    }

    public Long getUserId() {
        return userId;
    }

    public String getRealName() {
        return realName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getDataScope() {
        return dataScope;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    /** 超级管理员拥有全部权限。 */
    public boolean isSuperAdmin() {
        return "SYSTEM_ADMIN".equals(roleCode) || permissions.contains("*");
    }

    public boolean hasPermission(String permission) {
        if (isSuperAdmin()) {
            return true;
        }
        return permissions.contains(permission);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        if (roleCode != null) {
            list.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
        }
        for (String p : permissions) {
            list.add(new SimpleGrantedAuthority(p));
        }
        return list;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
