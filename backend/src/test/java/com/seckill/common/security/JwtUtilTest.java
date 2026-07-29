package com.seckill.common.security;

import com.seckill.common.constant.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JwtUtil} 单元测试。
 *
 * <p>纯 JWT 操作，不依赖 Spring 上下文。</p>
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    /** 32 字节 base64 编码的测试密钥 */
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "12345678901234567890123456789012".getBytes());

    private static final long ACCESS_EXP = 3600_000;   // 1h
    private static final long REFRESH_EXP = 7 * 24 * 3600_000; // 7d

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, ACCESS_EXP, REFRESH_EXP);
    }

    @Nested
    class GenerateAndValidate {

        @Test
        @DisplayName("签发并验证 access token -> claims 正确")
        void accessToken() {
            String token = jwtUtil.generateAccessToken(1L, "张三", UserRole.user);

            Claims claims = jwtUtil.validateAccessToken(token);
            assertThat(claims.getSubject()).isEqualTo("1");
            assertThat(jwtUtil.getUserId(claims)).isEqualTo(1L);
            assertThat(jwtUtil.getRole(claims)).isEqualTo(UserRole.user);
            assertThat(jwtUtil.getUserName(claims)).isEqualTo("张三");
        }

        @Test
        @DisplayName("签发并验证 refresh token -> claims 正确")
        void refreshToken() {
            String token = jwtUtil.generateRefreshToken(1L);

            Claims claims = jwtUtil.validateRefreshToken(token);
            assertThat(claims.getSubject()).isEqualTo("1");
            assertThat(jwtUtil.getUserId(claims)).isEqualTo(1L);
        }

        @Test
        @DisplayName("access token 不能当 refresh token 用")
        void accessToken_notValidAsRefresh() {
            String token = jwtUtil.generateAccessToken(1L, "张三", UserRole.user);

            assertThatThrownBy(() -> jwtUtil.validateRefreshToken(token))
                    .isInstanceOf(JwtException.class)
                    .hasMessageContaining("Invalid token type");
        }

        @Test
        @DisplayName("refresh token 不能当 access token 用")
        void refreshToken_notValidAsAccess() {
            String token = jwtUtil.generateRefreshToken(1L);

            assertThatThrownBy(() -> jwtUtil.validateAccessToken(token))
                    .isInstanceOf(JwtException.class)
                    .hasMessageContaining("Invalid token type");
        }

        @Test
        @DisplayName("不同用户签发不同的 token")
        void differentUsers() {
            String token1 = jwtUtil.generateAccessToken(1L, "张三", UserRole.user);
            String token2 = jwtUtil.generateAccessToken(2L, "李四", UserRole.merchant);

            assertThat(token1).isNotEqualTo(token2);
            assertThat(jwtUtil.getUserId(jwtUtil.validateAccessToken(token2))).isEqualTo(2L);
            assertThat(jwtUtil.getRole(jwtUtil.validateAccessToken(token2))).isEqualTo(UserRole.merchant);
        }
    }

    @Nested
    class ValidationFailures {

        @Test
        @DisplayName("过期 token -> ExpiredJwtException")
        void expired() throws Exception {
            // 使用 0ms 过期的 jwtUtil
            JwtUtil shortLived = new JwtUtil(TEST_SECRET, 1, REFRESH_EXP);
            String token = shortLived.generateAccessToken(1L, "张三", UserRole.user);

            Thread.sleep(10); // 保证过期

            assertThatThrownBy(() -> jwtUtil.validateAccessToken(token))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("错误签名的 token -> JwtException")
        void wrongSignature() {
            // 用不同的密钥签发
            String otherSecret = Base64.getEncoder().encodeToString(
                    "abcdefghijklmnopqrstuvwxyz123456".getBytes());
            JwtUtil other = new JwtUtil(otherSecret, ACCESS_EXP, REFRESH_EXP);
            String token = other.generateAccessToken(1L, "张三", UserRole.user);

            assertThatThrownBy(() -> jwtUtil.validateAccessToken(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("非法 token 字符串 -> JwtException")
        void garbageString() {
            assertThatThrownBy(() -> jwtUtil.validateAccessToken("not.a.jwt"))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("空字符串 -> 异常")
        void emptyString() {
            // jjwt 对空字符串抛出 IllegalArgumentException（而非 JwtException）
            assertThatThrownBy(() -> jwtUtil.validateAccessToken(""))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    class Constructor {

        @Test
        @DisplayName("密钥不足 32 字节 -> 启动时抛异常")
        void shortSecret_throws() {
            String shortSecret = Base64.getEncoder().encodeToString("short".getBytes());

            assertThatThrownBy(() -> new JwtUtil(shortSecret, ACCESS_EXP, REFRESH_EXP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("256 bits");
        }
    }
}
