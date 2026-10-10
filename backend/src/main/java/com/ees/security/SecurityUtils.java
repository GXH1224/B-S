package com.ees.security;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 当前登录用户工具。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<LoginUser> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof LoginUser lu)) {
            return Optional.empty();
        }
        return Optional.of(lu);
    }

    public static LoginUser require() {
        return currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期"));
    }

    public static String currentUsername() {
        return currentUser().map(LoginUser::getUsername).orElse("anonymous");
    }

    public static Long currentUserId() {
        return currentUser().map(LoginUser::getUserId).orElse(null);
    }

    public static boolean hasPermission(String permission) {
        return currentUser().map(u -> u.hasPermission(permission)).orElse(false);
    }

    public static void requirePermission(String permission) {
        if (!hasPermission(permission)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权执行该操作：" + permission);
        }
    }
}
