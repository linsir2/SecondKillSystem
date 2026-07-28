package com.seckill.module.user.service;

import com.seckill.common.constant.BanStatus;
import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.module.user.mapper.SysUserMapper;
import com.seckill.module.user.model.dto.UserBannedEvent;
import com.seckill.module.user.model.dto.UserInfo;
import com.seckill.module.user.model.dto.UserUnbannedEvent;
import com.seckill.module.user.model.entity.SysUser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private UserServiceImpl userService;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    // ========================================================================
    // helpers
    // ========================================================================

    private SysUser createUser(Long id, String name, UserRole role, BanStatus banStatus) {
        SysUser u = new SysUser();
        u.setUserId(id);
        u.setUserName(name);
        u.setEmail(name + "@test.com");
        u.setPassword("hashed_" + id);
        u.setRole(role);
        u.setBanStatus(banStatus);
        return u;
    }

    private void assertMatch(UserInfo info, Long id, String name, UserRole role, BanStatus banStatus) {
        assertThat(info.getUserId()).isEqualTo(id);
        assertThat(info.getUserName()).isEqualTo(name);
        assertThat(info.getEmail()).isEqualTo(name + "@test.com");
        assertThat(info.getRole()).isEqualTo(role);
        assertThat(info.getBanStatus()).isEqualTo(banStatus);
    }

    // ========================================================================
    // helpers for ban/unban lock
    // ========================================================================

    private void lockAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    private void lockContended() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
    }

    // ========================================================================
    // getUserInfo
    // ========================================================================

    @Nested
    class GetUserInfo {

        @Test
        void happyPath_merchant() {
            when(userMapper.selectById(1L)).thenReturn(createUser(1L, "商家A", UserRole.merchant, BanStatus.normal));

            UserInfo info = userService.getUserInfo(1L);

            assertMatch(info, 1L, "商家A", UserRole.merchant, BanStatus.normal);
        }

        @Test
        void happyPath_user() {
            when(userMapper.selectById(2L)).thenReturn(createUser(2L, "用户X", UserRole.user, BanStatus.normal));

            UserInfo info = userService.getUserInfo(2L);

            assertMatch(info, 2L, "用户X", UserRole.user, BanStatus.normal);
        }

        @Test
        void nullInput_throws() {
            assertThatThrownBy(() -> userService.getUserInfo(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void notFound_returnsNull() {
            when(userMapper.selectById(999L)).thenReturn(null);

            UserInfo info = userService.getUserInfo(999L);

            assertThat(info).isNull();
        }

        @Test
        void bannedUser_stillReturns() {
            when(userMapper.selectById(3L)).thenReturn(createUser(3L, "封禁用户", UserRole.user, BanStatus.banned));

            UserInfo info = userService.getUserInfo(3L);

            assertMatch(info, 3L, "封禁用户", UserRole.user, BanStatus.banned);
        }

        @Test
        void nullRole_defensive() {
            SysUser user = createUser(4L, "role为空", null, BanStatus.normal);
            when(userMapper.selectById(4L)).thenReturn(user);

            UserInfo info = userService.getUserInfo(4L);

            assertThat(info.getRole()).isNull();
            assertThat(info.getBanStatus()).isEqualTo(BanStatus.normal);
        }
    }

    // ========================================================================
    // getUserInfoList
    // ========================================================================

    @Nested
    class GetUserInfoList {

        @Test
        void happyPath_allExist() {
            when(userMapper.selectBatchIds(List.of(1L, 2L)))
                    .thenReturn(List.of(
                            createUser(1L, "商家A", UserRole.merchant, BanStatus.normal),
                            createUser(2L, "用户X", UserRole.user, BanStatus.normal)));

            List<UserInfo> list = userService.getUserInfoList(List.of(1L, 2L));

            assertThat(list).hasSize(2);
            assertMatch(list.get(0), 1L, "商家A", UserRole.merchant, BanStatus.normal);
            assertMatch(list.get(1), 2L, "用户X", UserRole.user, BanStatus.normal);
        }

        @Test
        void nullInput_throws() {
            assertThatThrownBy(() -> userService.getUserInfoList(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void emptyList_returnsEmpty() {
            List<UserInfo> list = userService.getUserInfoList(emptyList());

            assertThat(list).isEmpty();
        }

        @Test
        void partialExists_nullAtMissingPositions() {
            when(userMapper.selectBatchIds(List.of(1L, 999L, 2L)))
                    .thenReturn(List.of(
                            createUser(1L, "商家A", UserRole.merchant, BanStatus.normal),
                            createUser(2L, "用户X", UserRole.user, BanStatus.normal)));

            List<UserInfo> list = userService.getUserInfoList(List.of(1L, 999L, 2L));

            assertThat(list).hasSize(3);
            assertMatch(list.get(0), 1L, "商家A", UserRole.merchant, BanStatus.normal);
            assertThat(list.get(1)).isNull();
            assertMatch(list.get(2), 2L, "用户X", UserRole.user, BanStatus.normal);
        }

        @Test
        void orderPreservation_whenMapperReturnsDifferentOrder() {
            // selectBatchIds 用 IN(...)，不保证返回顺序
            when(userMapper.selectBatchIds(List.of(3L, 1L, 2L)))
                    .thenReturn(List.of(
                            createUser(1L, "用户A", UserRole.user, BanStatus.normal),
                            createUser(2L, "用户B", UserRole.user, BanStatus.normal),
                            createUser(3L, "用户C", UserRole.user, BanStatus.normal)));

            List<UserInfo> list = userService.getUserInfoList(List.of(3L, 1L, 2L));

            assertThat(list).hasSize(3);
            assertMatch(list.get(0), 3L, "用户C", UserRole.user, BanStatus.normal);
            assertMatch(list.get(1), 1L, "用户A", UserRole.user, BanStatus.normal);
            assertMatch(list.get(2), 2L, "用户B", UserRole.user, BanStatus.normal);
        }

        @Test
        void duplicateIds_returnsDuplicate() {
            when(userMapper.selectBatchIds(List.of(1L, 1L)))
                    .thenReturn(List.of(createUser(1L, "商家A", UserRole.merchant, BanStatus.normal)));

            List<UserInfo> list = userService.getUserInfoList(List.of(1L, 1L));

            assertThat(list).hasSize(2);
            assertMatch(list.get(0), 1L, "商家A", UserRole.merchant, BanStatus.normal);
            assertMatch(list.get(1), 1L, "商家A", UserRole.merchant, BanStatus.normal);
        }

        @Test
        void allNotFound_returnsAllNull() {
            when(userMapper.selectBatchIds(List.of(999L, 888L)))
                    .thenReturn(emptyList());

            List<UserInfo> list = userService.getUserInfoList(List.of(999L, 888L));

            assertThat(list).hasSize(2);
            assertThat(list.get(0)).isNull();
            assertThat(list.get(1)).isNull();
        }
    }

    // ========================================================================
    // banUser
    // ========================================================================

    @Nested
    class BanUser {

        private final Long operatorId = 100L;
        private final Long targetUserId = 200L;

        @Test
        void happyPath() {
            lockAcquired();
            when(userMapper.selectById(operatorId)).thenReturn(createUser(operatorId, "admin", UserRole.admin, BanStatus.normal));
            when(userMapper.selectById(targetUserId)).thenReturn(createUser(targetUserId, "user", UserRole.user, BanStatus.normal));
            when(redisTemplate.opsForSet()).thenReturn(setOps);

            userService.banUser(operatorId, targetUserId);

            verify(userMapper).updateById(any(SysUser.class));
            verify(setOps).add("seckill:blacklist", String.valueOf(targetUserId));
            verify(eventPublisher).publishEvent(any(UserBannedEvent.class));
        }

        @Test
        void operatorNotAdmin_throws() {
            lockAcquired();
            when(userMapper.selectById(operatorId)).thenReturn(createUser(operatorId, "merchant", UserRole.merchant, BanStatus.normal));

            assertThatThrownBy(() -> userService.banUser(operatorId, targetUserId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权");

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void targetUserNotFound_throws() {
            lockAcquired();
            when(userMapper.selectById(operatorId)).thenReturn(createUser(operatorId, "admin", UserRole.admin, BanStatus.normal));
            when(userMapper.selectById(targetUserId)).thenReturn(null);

            assertThatThrownBy(() -> userService.banUser(operatorId, targetUserId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不存在");

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void alreadyBanned_throws() {
            lockAcquired();
            when(userMapper.selectById(operatorId)).thenReturn(createUser(operatorId, "admin", UserRole.admin, BanStatus.normal));
            when(userMapper.selectById(targetUserId)).thenReturn(createUser(targetUserId, "banned", UserRole.user, BanStatus.banned));

            assertThatThrownBy(() -> userService.banUser(operatorId, targetUserId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已被封禁");

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void operatorNotFound_throws() {
            lockAcquired();
            when(userMapper.selectById(operatorId)).thenReturn(null);

            assertThatThrownBy(() -> userService.banUser(operatorId, targetUserId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权");

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void bannedAdmin_throws() {
            lockAcquired();
            when(userMapper.selectById(operatorId)).thenReturn(createUser(operatorId, "bannedAdmin", UserRole.admin, BanStatus.banned));

            assertThatThrownBy(() -> userService.banUser(operatorId, targetUserId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权");

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void lockContended_throws() {
            lockContended();

            assertThatThrownBy(() -> userService.banUser(operatorId, targetUserId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("操作过于频繁");

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    // ========================================================================
    // unbanUser
    // ========================================================================

    @Nested
    class UnbanUser {

        private final Long operatorId = 100L;
        private final Long targetUserId = 200L;

        @Test
        void happyPath() {
            lockAcquired();
            when(userMapper.selectById(operatorId)).thenReturn(createUser(operatorId, "admin", UserRole.admin, BanStatus.normal));
            when(userMapper.selectById(targetUserId)).thenReturn(createUser(targetUserId, "user", UserRole.user, BanStatus.banned));
            when(redisTemplate.opsForSet()).thenReturn(setOps);

            userService.unbanUser(operatorId, targetUserId);

            verify(userMapper).updateById(any(SysUser.class));
            verify(setOps).remove("seckill:blacklist", String.valueOf(targetUserId));
            verify(eventPublisher).publishEvent(any(UserUnbannedEvent.class));
        }

        @Test
        void notBanned_throws() {
            lockAcquired();
            when(userMapper.selectById(operatorId)).thenReturn(createUser(operatorId, "admin", UserRole.admin, BanStatus.normal));
            when(userMapper.selectById(targetUserId)).thenReturn(createUser(targetUserId, "normal", UserRole.user, BanStatus.normal));

            assertThatThrownBy(() -> userService.unbanUser(operatorId, targetUserId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未被封禁");

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void operatorNotFound_throws() {
            lockAcquired();
            when(userMapper.selectById(operatorId)).thenReturn(null);

            assertThatThrownBy(() -> userService.unbanUser(operatorId, targetUserId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权");

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void bannedAdmin_throws() {
            lockAcquired();
            when(userMapper.selectById(operatorId)).thenReturn(createUser(operatorId, "bannedAdmin", UserRole.admin, BanStatus.banned));

            assertThatThrownBy(() -> userService.unbanUser(operatorId, targetUserId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权");

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void lockContended_throws() {
            lockContended();

            assertThatThrownBy(() -> userService.unbanUser(operatorId, targetUserId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("操作过于频繁");

            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
