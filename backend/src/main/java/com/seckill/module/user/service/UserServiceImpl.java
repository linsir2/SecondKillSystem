package com.seckill.module.user.service;

import com.seckill.module.user.mapper.SysUserMapper;
import com.seckill.module.user.model.dto.UserInfo;
import com.seckill.module.user.model.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;

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

    private UserInfo toUserInfo(SysUser entity) {
        if (entity == null) {
            return null;
        }
        return new UserInfo(entity.getUserId(), entity.getUserName(),
                entity.getEmail(), entity.getRole(), entity.getBanStatus());
    }
}
