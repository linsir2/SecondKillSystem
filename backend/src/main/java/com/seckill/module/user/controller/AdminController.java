package com.seckill.module.user.controller;

import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员 REST 接口 —— 封禁管理。
 *
 * <p>封禁和解封委托 {@link UserService#banUser} / {@link UserService#unbanUser}，
 * Service 层已内建 Redis 分布式锁防止并发 + {@code @Transactional} 保证一致性。</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminController {

    private final UserService userService;

    /**
     * 封禁用户（仅管理员）。
     */
    @PostMapping("/users/{userId}/ban")
    public Result<Void> banUser(@PathVariable Long userId) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.admin) {
            throw new BusinessException("仅管理员可操作");
        }
        userService.banUser(user.getUserId(), userId);
        return Result.success(null);
    }

    /**
     * 解封用户（仅管理员）。
     */
    @PostMapping("/users/{userId}/unban")
    public Result<Void> unbanUser(@PathVariable Long userId) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.admin) {
            throw new BusinessException("仅管理员可操作");
        }
        userService.unbanUser(user.getUserId(), userId);
        return Result.success(null);
    }
}
