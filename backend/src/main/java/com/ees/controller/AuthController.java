package com.ees.controller;

import com.ees.common.ApiResponse;
import com.ees.dto.LoginRequest;
import com.ees.dto.LoginResponse;
import com.ees.dto.ChangePasswordRequest;
import com.ees.security.LoginUser;
import com.ees.security.SecurityUtils;
import com.ees.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** POST /api/auth/login 登录并返回会话信息 */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, httpRequest), "登录成功");
    }

    /** POST /api/auth/logout 登出 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(httpRequest);
        return ApiResponse.ok();
    }

    /**
     * GET /api/auth/me 当前用户信息。
     *
     * <p>必须与登录接口返回同样的 {@code menus}：前端刷新页面（F5）后不再调用登录接口，
     * 而是靠本接口恢复用户信息与菜单权限。若这里漏掉 menus，前端拿到的菜单集合为空，
     * 路由守卫会把所有带 {@code meta.menu} 的路由（包括驾驶舱自身）都重定向到驾驶舱，
     * 造成无限重定向、整页白屏。</p>
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        LoginUser u = SecurityUtils.require();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getUserId());
        m.put("username", u.getUsername());
        m.put("realName", u.getRealName());
        m.put("roleCode", u.getRoleCode());
        m.put("roleName", u.getRoleName());
        m.put("dataScope", u.getDataScope());
        m.put("permissions", u.getPermissions());
        m.put("menus", authService.menusOf(u.getRoleCode()));
        m.put("superAdmin", u.isSuperAdmin());
        return ApiResponse.ok(m);
    }

    /** POST /api/auth/change-password 修改本人密码 */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            HttpServletRequest httpRequest) {
        authService.changePassword(SecurityUtils.require().getUsername(), request, httpRequest);
        return ApiResponse.ok();
    }
}
