package com.seckill.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seckill.common.constant.BanStatus;
import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.security.JwtUtil;
import com.seckill.module.user.mapper.SysUserMapper;
import com.seckill.module.user.model.dto.*;
import com.seckill.module.user.model.entity.SysUser;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String BLACKLIST_KEY = "seckill:blacklist";

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final SysUserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // ========================================================================
    // OHS
    // ========================================================================

    @Override
    public UserInfo getUserInfo(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        SysUser user = userMapper.selectById(userId);
        return toUserInfo(user);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<UserInfo> getUserInfoList(List<Long> userIds) {
        if (userIds == null) {
            throw new IllegalArgumentException("userIds must not be null");
        }
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysUser> users = userMapper.selectBatchIds(userIds);
        Map<Long, SysUser> map = users.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(u -> u.getUserId(), Function.identity(), (a, b) -> a));

        return userIds.stream()
                .map(id -> toUserInfo(map.get(id)))
                .collect(Collectors.toList());
    }

    // ========================================================================
    // 封禁 / 解封
    // ========================================================================

    @Override
    @Transactional
    public void banUser(Long operatorId, Long userId) {
        // 分布式锁：防止并发封禁重复发布事件
        String lockKey = "seckill:ban:lock:" + userId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(3));
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("操作过于频繁，请稍后重试");
        }
        try {
            // 校验操作者身份（admin 且未被封禁）
            UserInfo operator = getUserInfo(operatorId);
            if (operator == null || operator.getRole() != UserRole.admin || operator.getBanStatus() == BanStatus.banned) {
                throw new BusinessException("无权执行该操作");
            }

            SysUser user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }

            try {
                user.ban();
            } catch (IllegalStateException e) {
                throw new BusinessException(e.getMessage());
            }

            userMapper.updateById(user);
            redisTemplate.opsForSet().add(BLACKLIST_KEY, String.valueOf(userId));
            eventPublisher.publishEvent(new UserBannedEvent(userId, operatorId));
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    @Transactional
    public void unbanUser(Long operatorId, Long userId) {
        String lockKey = "seckill:ban:lock:" + userId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(3));
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("操作过于频繁，请稍后重试");
        }
        try {
            UserInfo operator = getUserInfo(operatorId);
            if (operator == null || operator.getRole() != UserRole.admin || operator.getBanStatus() == BanStatus.banned) {
                throw new BusinessException("无权执行该操作");
            }

            SysUser user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }

            try {
                user.unban();
            } catch (IllegalStateException e) {
                throw new BusinessException(e.getMessage());
            }

            userMapper.updateById(user);
            redisTemplate.opsForSet().remove(BLACKLIST_KEY, String.valueOf(userId));
            eventPublisher.publishEvent(new UserUnbannedEvent(userId, operatorId));
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    // ========================================================================
    // 注册
    // ========================================================================

    @Override
    @Transactional
    public UserRegisteredEvent register(RegisterRequest req) {
        // ---- 1. 唯一性校验 ----
        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("user_name", req.userName())) > 0) {
            throw new BusinessException("用户名已被注册");
        }
        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("email", req.email())) > 0) {
            throw new BusinessException("邮箱已被注册");
        }

        // ---- 2. 密码编码 ----
        String encodedPassword = passwordEncoder.encode(req.password());

        // ---- 3. 构建用户 ----
        SysUser user = new SysUser();
        user.setUserName(req.userName());
        user.setEmail(req.email());
        user.setPassword(encodedPassword);
        user.setRole(req.resolveRole());
        user.setBanStatus(BanStatus.normal);

        userMapper.insert(user);

        // ---- 4. 发布事件 ----
        UserRegisteredEvent event = new UserRegisteredEvent(user.getUserId(), user.getEmail(), user.getRole());
        eventPublisher.publishEvent(event);

        return event;
    }

    // ========================================================================
    // 登录
    // ========================================================================

    @Override
    public LoginVO login(LoginRequest req) {
        // ---- 1. 查找用户 ----
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("email", req.email()));
        if (user == null) {
            throw new BusinessException("邮箱或密码错误");
        }

        // ---- 2. 校验密码 ----
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BusinessException("邮箱或密码错误");
        }

        // ---- 3. 封禁检查 ----
        if (user.getBanStatus() == BanStatus.banned) {
            throw new BusinessException("账户已被封禁");
        }

        // ---- 4. 签发 token ----
        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getUserName(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());

        return new LoginVO(accessToken, refreshToken, user.getUserId(), user.getUserName(), user.getRole());
    }

    // ========================================================================
    // 刷新 token
    // ========================================================================

    @Override
    public LoginVO refresh(String refreshToken) {
        // ---- 0. 黑名单检查 ----
        if (isRefreshTokenBlacklisted(refreshToken)) {
            throw new BusinessException("登录已过期，请重新登录");
        }

        Claims claims;
        try {
            claims = jwtUtil.validateRefreshToken(refreshToken);
        } catch (Exception e) {
            throw new BusinessException("登录已过期，请重新登录");
        }

        Long userId = jwtUtil.getUserId(claims);
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // ---- 3. 封禁检查（与 login 一致） ----
        if (user.getBanStatus() == BanStatus.banned) {
            throw new BusinessException("账户已被封禁");
        }

        // ---- 4. 签发新 token 对（refresh token 也刷新，延长有效期） ----
        String newAccess = jwtUtil.generateAccessToken(user.getUserId(), user.getUserName(), user.getRole());
        String newRefresh = jwtUtil.generateRefreshToken(user.getUserId());

        // ---- 5. 废弃旧 refresh token（防重放） ----
        try {
            long refreshTtl = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (refreshTtl > 0) {
                redisTemplate.opsForValue().set(
                        "seckill:rbl:" + JwtUtil.sha256Hex(refreshToken), "1",
                        Duration.ofMillis(refreshTtl));
            }
        } catch (Exception e) {
            log.debug("Failed to blacklist old refresh token: {}", e.getMessage());
        }

        return new LoginVO(newAccess, newRefresh, user.getUserId(), user.getUserName(), user.getRole());
    }

    // ========================================================================
    // 当前用户信息
    // ========================================================================

    @Override
    public UserInfoVO getCurrentUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return new UserInfoVO(user.getUserId(), user.getUserName(), user.getEmail(),
                user.getRole(), user.getBanStatus());
    }

    // ========================================================================
    // 登出
    // ========================================================================

    @Override
    public void logout(Long userId, String refreshToken, String accessToken) {
        try {
            Claims accessClaims = jwtUtil.validateAccessToken(accessToken);
            long accessTtl = accessClaims.getExpiration().getTime() - System.currentTimeMillis();
            if (accessTtl > 0) {
                redisTemplate.opsForValue().set(
                        "seckill:abl:" + JwtUtil.sha256Hex(accessToken), "1",
                        Duration.ofMillis(accessTtl));
            }
        } catch (Exception e) {
            log.debug("Failed to blacklist access token for user {}: {}", userId, e.getMessage());
        }

        try {
            Claims refreshClaims = jwtUtil.validateRefreshToken(refreshToken);
            long refreshTtl = refreshClaims.getExpiration().getTime() - System.currentTimeMillis();
            if (refreshTtl > 0) {
                redisTemplate.opsForValue().set(
                        "seckill:rbl:" + JwtUtil.sha256Hex(refreshToken), "1",
                        Duration.ofMillis(refreshTtl));
            }
        } catch (Exception e) {
            log.debug("Failed to blacklist refresh token for user {}: {}", userId, e.getMessage());
        }
    }

    // ========================================================================
    // 转换
    // ========================================================================

    private UserInfo toUserInfo(SysUser entity) {
        if (entity == null) {
            return null;
        }
        return new UserInfo(entity.getUserId(), entity.getUserName(),
                entity.getEmail(), entity.getRole(), entity.getBanStatus());
    }

    private boolean isRefreshTokenBlacklisted(String refreshToken) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey("seckill:rbl:" + JwtUtil.sha256Hex(refreshToken)));
        } catch (Exception e) {
            log.debug("Refresh token blacklist check failed: {}", e.getMessage());
            return false;
        }
    }
}
