package com.ees.controller;

import com.ees.common.ApiResponse;
import com.ees.common.PageResult;
import com.ees.domain.SysRole;
import com.ees.domain.SysUser;
import com.ees.dto.UserSaveRequest;
import com.ees.repository.SysRoleRepository;
import com.ees.repository.SysUserRepository;
import com.ees.security.SecurityUtils;
import com.ees.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户与角色管理接口。仅系统管理员可操作。
 */
@RestController
@RequestMapping("/api")
public class UserController {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final AuthService authService;

    public UserController(SysUserRepository userRepository, SysRoleRepository roleRepository,
                          AuthService authService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authService = authService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('system:user:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<PageResult<SysUser>> search(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String roleCode,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Page<SysUser> p = userRepository.search(blank(keyword), blank(roleCode), blank(status),
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.ASC, "id")));
        return ApiResponse.ok(PageResult.of(p.getContent(), p.getTotalElements(), page, size));
    }

    @GetMapping("/users/options")
    public ApiResponse<List<Map<String, Object>>> options() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysUser u : userRepository.findAllByDeletedFalseOrderByIdAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("roleCode", u.getRoleCode());
            m.put("orgName", u.getOrgName());
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('system:user:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<SysUser> get(@PathVariable Long id) {
        return ApiResponse.ok(userRepository.findByIdAndDeletedFalse(id).orElse(null));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('system:user:create') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<SysUser> create(@Valid @RequestBody UserSaveRequest request) {
        request.setId(null);
        return ApiResponse.ok(authService.saveUser(request, SecurityUtils.currentUsername()), "用户创建成功");
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('system:user:update') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<SysUser> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        request.setId(id);
        return ApiResponse.ok(authService.saveUser(request, SecurityUtils.currentUsername()), "用户更新成功");
    }

    /** 重置密码 */
    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:update') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable Long id,
                                                          @RequestBody Map<String, String> body) {
        SysUser u = userRepository.findByIdAndDeletedFalse(id).orElse(null);
        if (u == null) {
            return ApiResponse.fail("用户不存在");
        }
        String pwd = body.getOrDefault("newPassword", "123456");
        UserSaveRequest req = new UserSaveRequest();
        req.setId(u.getId());
        req.setUsername(u.getUsername());
        req.setPassword(pwd);
        req.setRealName(u.getRealName());
        req.setRoleCode(u.getRoleCode());
        req.setOrgName(u.getOrgName());
        req.setEmail(u.getEmail());
        req.setPhone(u.getPhone());
        req.setStatus(u.getStatus());
        req.setRemark(u.getRemark());
        authService.saveUser(req, SecurityUtils.currentUsername());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("newPassword", pwd);
        return ApiResponse.ok(m, "密码已重置");
    }

    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('system:user:update') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<SysUser> changeStatus(@PathVariable Long id,
                                             @RequestParam String status) {
        SysUser u = userRepository.findByIdAndDeletedFalse(id).orElse(null);
        if (u == null) {
            return ApiResponse.fail("用户不存在");
        }
        u.setStatus(status);
        if (!"LOCKED".equals(status)) {
            u.setLockedUntil(null);
            u.setLoginFailCount(0);
        }
        u.setUpdatedBy(SecurityUtils.currentUsername());
        return ApiResponse.ok(userRepository.save(u), "状态已更新");
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('system:user:delete') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysUser u = userRepository.findByIdAndDeletedFalse(id).orElse(null);
        if (u == null) {
            return ApiResponse.fail("用户不存在");
        }
        if ("admin".equals(u.getUsername())) {
            return ApiResponse.fail("内置管理员账号不允许删除");
        }
        u.setDeleted(true);
        userRepository.save(u);
        return ApiResponse.ok();
    }

    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> roles() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysRole r : roleRepository.findAllByOrderBySortOrderAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("roleCode", r.getRoleCode());
            m.put("roleName", r.getRoleName());
            m.put("description", r.getDescription());
            m.put("dataScope", r.getDataScope());
            m.put("builtIn", r.getBuiltIn());
            m.put("userCount", userRepository.countByRoleCodeAndDeletedFalse(r.getRoleCode()));
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/roles/{roleCode}/permissions")
    @PreAuthorize("hasAuthority('system:role:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> rolePermissions(@PathVariable String roleCode) {
        SysRole r = roleRepository.findByRoleCode(roleCode).orElse(null);
        if (r == null) {
            return ApiResponse.fail("角色不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roleCode", r.getRoleCode());
        m.put("roleName", r.getRoleName());
        m.put("permissions", com.ees.common.JsonUtils.toStringList(r.getPermissions()));
        return ApiResponse.ok(m);
    }

    /** 权限字典，供前端做按钮级控制说明。 */
    @GetMapping("/permissions")
    public ApiResponse<List<Map<String, Object>>> permissions() {
        List<Map<String, Object>> out = new ArrayList<>();
        String[][] dict = {
                {"dashboard:view", "查看驾驶舱"},
                {"project:list", "查看项目"}, {"project:create", "创建项目"},
                {"project:update", "编辑项目"}, {"project:member", "配置项目成员"},
                {"indicator:list", "查看指标体系"}, {"indicator:import", "导入指标体系"},
                {"data:source:list", "查看数据源"}, {"data:source:create", "新建数据源"},
                {"data:source:update", "编辑/删除数据源"}, {"data:source:test", "测试数据源连接"},
                {"data:source:extract", "执行数据抽取"},
                {"data:batch:list", "查看数据批次"}, {"data:batch:import", "导入数据批次"},
                {"data:batch:validate", "重新校验数据"}, {"data:snapshot:list", "查看数据快照"},
                {"data:snapshot:create", "生成数据快照"},
                {"task:list", "查看任务"}, {"task:create", "创建评估任务"},
                {"task:execute", "执行评估任务"}, {"task:cancel", "取消任务"}, {"task:retry", "重试任务"},
                {"result:list", "查看结果"}, {"result:view", "查看结果详情"},
                {"result:review", "结果复核"}, {"result:publish", "结果发布"},
                {"result:compare", "结果横向对比"},
                {"result:export", "结果导出"},
                {"report:list", "查看报告"}, {"report:create", "生成报告"}, {"report:download", "下载报告"},
                {"baseline:list", "查看算法基准"},
                {"system:user:list", "查看用户"}, {"system:audit:list", "查看审计日志"},
                {"system:config:list", "查看系统参数"}
        };
        for (String[] d : dict) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", d[0]);
            m.put("label", d[1]);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
