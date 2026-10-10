package com.ees.service;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.common.JsonUtils;
import com.ees.config.EesProperties;
import com.ees.domain.SysRole;
import com.ees.domain.SysUser;
import com.ees.dto.ChangePasswordRequest;
import com.ees.dto.LoginRequest;
import com.ees.dto.LoginResponse;
import com.ees.dto.UserSaveRequest;
import com.ees.repository.SysRoleRepository;
import com.ees.repository.SysUserRepository;
import com.ees.security.JwtService;
import com.ees.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 认证与用户服务。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final EesProperties properties;
    private final Environment environment;

    public AuthService(SysUserRepository userRepository, SysRoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuditService auditService, EesProperties properties,
                       Environment environment) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.properties = properties;
        this.environment = environment;
    }

    /** 解析角色权限集合。 */
    public Set<String> permissionsOf(String roleCode) {
        SysRole role = roleRepository.findByRoleCode(roleCode).orElse(null);
        if (role == null) {
            return new HashSet<>();
        }
        List<String> list = JsonUtils.toStringList(role.getPermissions());
        return new LinkedHashSet<>(list);
    }

    public String dataScopeOf(String roleCode) {
        return roleRepository.findByRoleCode(roleCode).map(SysRole::getDataScope).orElse("PROJECT");
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername().trim();
        SysUser user = userRepository.findByUsernameAndDeletedFalse(username).orElse(null);

        if (user == null) {
            auditService.recordSync("AUTH", "LOGIN", "USER", username,
                    "用户名不存在", false, "用户名或密码错误", null, username, httpRequest);
            throw new BizException(ErrorCode.LOGIN_FAILED, "用户名或密码错误");
        }
        if ("DISABLED".equals(user.getStatus())) {
            auditService.recordSync("AUTH", "LOGIN", "USER", username,
                    "账号已停用", false, "账号已停用", user.getId(), username, httpRequest);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "账号已停用，请联系系统管理员");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            auditService.recordSync("AUTH", "LOGIN", "USER", username,
                    "账号锁定中", false, "账号已锁定", user.getId(), username, httpRequest);
            throw new BizException(ErrorCode.ACCOUNT_LOCKED,
                    "账号已锁定至 " + user.getLockedUntil().format(DTF));
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int fail = user.getLoginFailCount() == null ? 0 : user.getLoginFailCount() + 1;
            user.setLoginFailCount(fail);
            int maxFail = properties.getSecurity().getMaxLoginFail();
            if (fail >= maxFail) {
                user.setStatus("LOCKED");
                user.setLockedUntil(LocalDateTime.now().plusMinutes(properties.getSecurity().getLockMinutes()));
                log.warn("用户 {} 连续登录失败 {} 次，已锁定", username, fail);
            }
            userRepository.save(user);
            auditService.recordSync("AUTH", "LOGIN", "USER", username,
                    "密码错误，累计 " + fail + " 次", false, "用户名或密码错误",
                    user.getId(), username, httpRequest);
            throw new BizException(ErrorCode.LOGIN_FAILED, "用户名或密码错误");
        }

        // 登录成功：复位失败计数
        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        if ("LOCKED".equals(user.getStatus())) {
            user.setStatus("ENABLED");
        }
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(AuditService.clientIp(httpRequest));
        userRepository.save(user);

        Set<String> perms = permissionsOf(user.getRoleCode());
        String scope = dataScopeOf(user.getRoleCode());
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), null,
                user.getRealName(), user.getRoleCode(), roleName(user.getRoleCode()), scope, perms, true);

        LoginResponse resp = new LoginResponse();
        resp.setToken(jwtService.generate(loginUser));
        resp.setExpiresIn(jwtService.expireSeconds());
        LoginResponse.UserInfo info = new LoginResponse.UserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setRealName(user.getRealName());
        info.setRoleCode(user.getRoleCode());
        info.setRoleName(loginUser.getRoleName());
        info.setOrgName(user.getOrgName());
        info.setDataScope(scope);
        info.setPermissions(perms);
        info.setMenus(menusOf(user.getRoleCode()));
        info.setLastLoginAt(user.getLastLoginAt() == null ? null : user.getLastLoginAt().format(DTF));
        resp.setUser(info);

        auditService.recordSync("AUTH", "LOGIN", "USER", username,
                "登录成功", true, null, user.getId(), username, httpRequest);
        return resp;
    }

    @Transactional
    public void logout(HttpServletRequest httpRequest) {
        auditService.recordSync("AUTH", "LOGOUT", "USER", null, "用户登出", true, null,
                com.ees.security.SecurityUtils.currentUserId(),
                com.ees.security.SecurityUtils.currentUsername(), httpRequest);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request, HttpServletRequest httpRequest) {
        SysUser user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原密码不正确");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedBy(username);
        userRepository.save(user);
        auditService.recordSync("CONFIG", "CHANGE_PASSWORD", "USER", username,
                "修改密码", true, null, user.getId(), username, httpRequest);
    }

    @Transactional
    public SysUser saveUser(UserSaveRequest request, String operator) {
        SysUser user;
        if (request.getId() != null) {
            user = userRepository.findByIdAndDeletedFalse(request.getId())
                    .orElseThrow(() -> BizException.notFound("用户不存在"));
            if (!user.getUsername().equals(request.getUsername())
                    && userRepository.existsByUsernameAndDeletedFalse(request.getUsername())) {
                throw new BizException(ErrorCode.DATA_CONFLICT, "用户名已存在：" + request.getUsername());
            }
        } else {
            if (userRepository.existsByUsernameAndDeletedFalse(request.getUsername())) {
                throw new BizException(ErrorCode.DATA_CONFLICT, "用户名已存在：" + request.getUsername());
            }
            user = new SysUser();
            user.setCreatedBy(operator);
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "新增用户必须设置初始密码");
            }
        }
        if (!roleRepository.existsByRoleCode(request.getRoleCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色不存在：" + request.getRoleCode());
        }
        user.setUsername(request.getUsername().trim());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setRealName(request.getRealName());
        user.setRoleCode(request.getRoleCode());
        user.setOrgName(request.getOrgName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(request.getStatus() == null ? "ENABLED" : request.getStatus());
        if (!"LOCKED".equals(user.getStatus())) {
            user.setLockedUntil(null);
            user.setLoginFailCount(0);
        }
        user.setRemark(request.getRemark());
        user.setUpdatedBy(operator);
        return userRepository.save(user);
    }

    private String roleName(String roleCode) {
        return roleRepository.findByRoleCode(roleCode).map(SysRole::getRoleName).orElse(roleCode);
    }

    /**
     * 前端菜单/路由授权标识。
     *
     * <p>登录接口与 {@code /api/auth/me} 都必须返回它，且两处必须来自同一个方法：
     * 前端刷新页面时走的是 {@code /api/auth/me}，若那里不返回菜单，
     * 前端会认为当前角色没有任何菜单权限，从而把所有受菜单保护的路由都重定向到
     * 驾驶舱（含驾驶舱自己），形成无限重定向 —— 表现为刷新后整页白屏。</p>
     */
    public List<String> menusOf(String roleCode) {
        return switch (roleCode) {
            case "SYSTEM_ADMIN" -> List.of("dashboard", "project", "indicator", "dataSource", "dataBatch",
                    "snapshot", "task", "result", "report", "baseline", "user", "role", "audit", "config");
            case "EVALUATOR" -> List.of("dashboard", "project", "indicator", "dataSource", "dataBatch",
                    "snapshot", "task", "result", "baseline");
            default -> List.of("dashboard");
        };
    }

    /**
     * 首次启动时创建默认账号（仅当用户表为空）。
     *
     * <p>系统只保留「系统管理员」与「评估人员」两类角色，因此默认账号也只有这两个。</p>
     */
    @Transactional
    public void initDefaultUsers() {
        if (userRepository.count() > 0) {
            return;
        }
        log.info("用户表为空，创建默认账号...");
        createUser("admin", "admin123", "系统管理员", "SYSTEM_ADMIN", "信息中心");
        createUser("evaluator", "eval123", "评估人员", "EVALUATOR", "评估组");
        // 技术方案 9.1 明确要求"日志不得打印密码和敏感配置"，因此初始口令只在 dev 档输出，
        // 便于本地开发时直接登录；prod 生产档只输出账号名，初始口令见交付文档 docs/DEPLOY.md。
        if (isDevProfile()) {
            log.info("默认账号创建完成：admin/admin123, evaluator/eval123");
            log.warn("以上为初始口令，首次登录后请立即修改。");
        } else {
            log.info("默认账号创建完成：admin、evaluator。初始口令见交付文档，首次登录后请立即修改。");
        }
    }

    /** 是否处于开发档（dev 档允许把初始口令打印到控制台，生产档禁止，见技术方案 9.1）。 */
    private boolean isDevProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    private void createUser(String username, String rawPassword, String realName,
                            String roleCode, String orgName) {        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRealName(realName);
        u.setRoleCode(roleCode);
        u.setOrgName(orgName);
        u.setStatus("ENABLED");
        u.setCreatedBy("system");
        u.setRemark("系统初始化账号，首次登录后请修改密码");
        userRepository.save(u);
    }

}
