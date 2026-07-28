package com.seckill.module.user.service;

import com.seckill.common.constant.BanStatus;
import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.module.user.mapper.SysUserMapper;
import com.seckill.module.user.model.dto.UserBannedEvent;
import com.seckill.module.user.model.dto.UserInfo;
import com.seckill.module.user.model.dto.UserUnbannedEvent;
import com.seckill.module.user.model.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String BLACKLIST_KEY = "seckill:blacklist";

    private final SysUserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

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
    // 转换
    // ========================================================================

    private UserInfo toUserInfo(SysUser entity) {
        if (entity == null) {
            return null;
        }
        return new UserInfo(entity.getUserId(), entity.getUserName(),
                entity.getEmail(), entity.getRole(), entity.getBanStatus());
    }
}
