package com.seckill.module.user.controller;

import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.user.model.dto.*;
import com.seckill.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 REST 接口 —— 注册 / 登录 / 刷新 / 当前用户。
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 注册新用户（成功后自动签发 token，无需再次登录）。
     */
    @PostMapping("/api/v1/auth/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest req) {
        userService.register(req);
        return Result.success(userService.login(new LoginRequest(req.email(), req.password())));
    }

    /**
     * 登录。
     */
    @PostMapping("/api/v1/auth/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(userService.login(req));
    }

    /**
     * 刷新 access token。
     */
    @PostMapping("/api/v1/auth/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return Result.success(userService.refresh(req.refreshToken()));
    }

    /**
     * 获取当前用户信息（需登录）。
     */
    @GetMapping("/api/v1/user/me")
    public Result<UserInfoVO> me() {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(userService.getCurrentUser(user.getUserId()));
    }
}
