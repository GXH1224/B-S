package com.ees.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
public class LoginResponse {

    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UserInfo user;

    @Getter
    @Setter
    public static class UserInfo {
        private Long id;
        private String username;
        private String realName;
        private String roleCode;
        private String roleName;
        private String orgName;
        private String dataScope;
        private Set<String> permissions;
        private List<String> menus;
        private String lastLoginAt;
    }
}
