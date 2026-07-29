package com.seckill.module.user.service;

import com.seckill.common.constant.BanStatus;
import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.security.JwtUtil;
import com.seckill.module.user.mapper.SysUserMapper;
import com.seckill.module.user.model.dto.*;
import com.seckill.module.user.model.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import java.util.Date;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

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

    // ========================================================================
    // register
    // ========================================================================

    @Nested
    class Register {

        private final RegisterRequest req = new RegisterRequest("新用户", "new@test.com", "password123", UserRole.user);

        @Test
        void happyPath_user() {
            when(userMapper.selectCount(any())).thenReturn(0L);
            when(passwordEncoder.encode("password123")).thenReturn("encoded_pwd");
            // insert 时模拟 MyBatis-Plus 回写 userId
            doAnswer(inv -> {
                SysUser u = inv.getArgument(0);
                u.setUserId(10001L);
                return 1;
            }).when(userMapper).insert(any(SysUser.class));

            UserRegisteredEvent event = userService.register(req);

            assertThat(event.email()).isEqualTo("new@test.com");
            assertThat(event.role()).isEqualTo(UserRole.user);
            assertThat(event.userId()).isNotNull();

            verify(userMapper).insert(any(SysUser.class));
            verify(eventPublisher).publishEvent(event);
        }

        @Test
        void happyPath_merchant() {
            RegisterRequest merchantReq = new RegisterRequest("商家A", "merchant@test.com", "password123", UserRole.merchant);
            when(userMapper.selectCount(any())).thenReturn(0L);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded_pwd");

            UserRegisteredEvent event = userService.register(merchantReq);

            assertThat(event.role()).isEqualTo(UserRole.merchant);
        }

        @Test
        void duplicateUserName_throws() {
            when(userMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> userService.register(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户名已被注册");
        }

        @Test
        void duplicateEmail_throws() {
            when(userMapper.selectCount(any())).thenReturn(0L).thenReturn(1L);

            assertThatThrownBy(() -> userService.register(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("邮箱已被注册");
        }

        @Test
        void roleDefaultsToUser() {
            RegisterRequest reqNoRole = new RegisterRequest("无名", "no@test.com", "password123", null);
            when(userMapper.selectCount(any())).thenReturn(0L);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");

            UserRegisteredEvent event = userService.register(reqNoRole);

            assertThat(event.role()).isEqualTo(UserRole.user);
        }
    }

    // ========================================================================
    // login
    // ========================================================================

    @Nested
    class Login {

        private final LoginRequest req = new LoginRequest("user@test.com", "correct_pwd");

        private SysUser mockUser() {
            SysUser u = new SysUser();
            u.setUserId(1L);
            u.setUserName("张三");
            u.setEmail("user@test.com");
            u.setPassword("encoded_pwd");
            u.setRole(UserRole.user);
            u.setBanStatus(BanStatus.normal);
            return u;
        }

        @Test
        void happyPath() {
            SysUser user = mockUser();
            when(userMapper.selectOne(any())).thenReturn(user);
            when(passwordEncoder.matches("correct_pwd", "encoded_pwd")).thenReturn(true);
            when(jwtUtil.generateAccessToken(1L, "张三", UserRole.user)).thenReturn("access");
            when(jwtUtil.generateRefreshToken(1L)).thenReturn("refresh");

            LoginVO vo = userService.login(req);

            assertThat(vo.accessToken()).isEqualTo("access");
            assertThat(vo.refreshToken()).isEqualTo("refresh");
            assertThat(vo.userId()).isEqualTo(1L);
            assertThat(vo.userName()).isEqualTo("张三");
            assertThat(vo.role()).isEqualTo(UserRole.user);
        }

        @Test
        void emailNotFound_throws() {
            when(userMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> userService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("邮箱或密码错误");
        }

        @Test
        void wrongPassword_throws() {
            when(userMapper.selectOne(any())).thenReturn(mockUser());
            when(passwordEncoder.matches("correct_pwd", "encoded_pwd")).thenReturn(false);

            assertThatThrownBy(() -> userService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("邮箱或密码错误");
        }

        @Test
        void bannedUser_throws() {
            SysUser user = mockUser();
            user.setBanStatus(BanStatus.banned);
            when(userMapper.selectOne(any())).thenReturn(user);
            when(passwordEncoder.matches("correct_pwd", "encoded_pwd")).thenReturn(true);

            assertThatThrownBy(() -> userService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已被封禁");
        }
    }

    // ========================================================================
    // refresh
    // ========================================================================

    @Nested
    class Refresh {

        @Test
        void happyPath() {
            Claims claims = mock(Claims.class);
            when(jwtUtil.validateRefreshToken("valid_token")).thenReturn(claims);
            when(jwtUtil.getUserId(claims)).thenReturn(1L);
            when(jwtUtil.generateAccessToken(1L, "张三", UserRole.user)).thenReturn("new_access");
            when(jwtUtil.generateRefreshToken(1L)).thenReturn("new_refresh");
            when(userMapper.selectById(1L)).thenReturn(createUser(1L, "张三", UserRole.user, BanStatus.normal));

            LoginVO vo = userService.refresh("valid_token");

            assertThat(vo.accessToken()).isEqualTo("new_access");
            assertThat(vo.refreshToken()).isEqualTo("new_refresh");
        }

        @Test
        void invalidToken_throws() {
            when(jwtUtil.validateRefreshToken("bad_token")).thenThrow(new JwtException("invalid"));

            assertThatThrownBy(() -> userService.refresh("bad_token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("登录已过期");
        }

        @Test
        void userNotFound_throws() {
            Claims claims = mock(Claims.class);
            when(jwtUtil.validateRefreshToken("token")).thenReturn(claims);
            when(jwtUtil.getUserId(claims)).thenReturn(999L);
            when(userMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> userService.refresh("token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户不存在");
        }

        @Test
        void bannedUser_throws() {
            Claims claims = mock(Claims.class);
            when(jwtUtil.validateRefreshToken("token")).thenReturn(claims);
            when(jwtUtil.getUserId(claims)).thenReturn(1L);
            when(userMapper.selectById(1L)).thenReturn(createUser(1L, "张三", UserRole.user, BanStatus.banned));

            assertThatThrownBy(() -> userService.refresh("token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("被封禁");
        }

        @Test
        void blacklistedRefreshToken_throws() {
            when(redisTemplate.hasKey(anyString())).thenReturn(true);

            assertThatThrownBy(() -> userService.refresh("revoked"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("登录已过期");
            verify(jwtUtil, never()).validateRefreshToken(anyString());
        }
    }

    // ========================================================================
    // getCurrentUser
    // ========================================================================

    @Nested
    class GetCurrentUser {

        @Test
        void happyPath() {
            when(userMapper.selectById(1L)).thenReturn(createUser(1L, "张三", UserRole.user, BanStatus.normal));

            UserInfoVO vo = userService.getCurrentUser(1L);

            assertThat(vo.userId()).isEqualTo(1L);
            assertThat(vo.userName()).isEqualTo("张三");
            assertThat(vo.email()).isEqualTo("张三@test.com");
            assertThat(vo.role()).isEqualTo(UserRole.user);
            assertThat(vo.banStatus()).isEqualTo(BanStatus.normal);
        }

        @Test
        void notFound_throws() {
            when(userMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> userService.getCurrentUser(999L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ========================================================================
    // logout
    // ========================================================================

    @Nested
    class Logout {

        @Test
        void happyPath() {
            Claims accessClaims = mock(Claims.class);
            when(accessClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 3_600_000));
            when(jwtUtil.validateAccessToken("access_token")).thenReturn(accessClaims);

            Claims refreshClaims = mock(Claims.class);
            when(refreshClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 604_800_000));
            when(jwtUtil.validateRefreshToken("refresh_token")).thenReturn(refreshClaims);

            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            userService.logout(1L, "refresh_token", "access_token");

            verify(valueOps).set(argThat(k -> k.startsWith("seckill:abl:")), eq("1"), any(Duration.class));
            verify(valueOps).set(argThat(k -> k.startsWith("seckill:rbl:")), eq("1"), any(Duration.class));
        }

        @Test
        void invalidAccessToken_doesNotThrow() {
            when(jwtUtil.validateAccessToken("bad")).thenThrow(new JwtException("expired"));

            Claims refreshClaims = mock(Claims.class);
            when(refreshClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 604_800_000));
            when(jwtUtil.validateRefreshToken("refresh_token")).thenReturn(refreshClaims);

            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            userService.logout(1L, "refresh_token", "bad");

            verify(valueOps, never()).set(argThat(k -> k.startsWith("seckill:abl:")), anyString(), any());
            verify(valueOps).set(argThat(k -> k.startsWith("seckill:rbl:")), eq("1"), any(Duration.class));
        }

        @Test
        void expiredToken_skipsBlacklist() {
            Claims accessClaims = mock(Claims.class);
            when(accessClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() - 1000));
            when(jwtUtil.validateAccessToken("expired")).thenReturn(accessClaims);

            Claims refreshClaims = mock(Claims.class);
            when(refreshClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 604_800_000));
            when(jwtUtil.validateRefreshToken("refresh_token")).thenReturn(refreshClaims);

            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            userService.logout(1L, "refresh_token", "expired");

            verify(valueOps, never()).set(argThat(k -> k.startsWith("seckill:abl:")), anyString(), any());
            verify(valueOps).set(argThat(k -> k.startsWith("seckill:rbl:")), eq("1"), any(Duration.class));
        }
    }
}