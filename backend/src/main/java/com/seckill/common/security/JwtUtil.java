package com.seckill.common.security;

import com.seckill.common.constant.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * JWT 工具 —— 签发/校验/解析 access token 和 refresh token。
 *
 * <p>access token: 24h，含 userId + userName + role<br>
 * refresh token: 7d，仅含 userId，type=refresh</p>
 */
@Component
public class JwtUtil {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USERNAME = "userName";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final long accessExpiration;   // ms
    private final long refreshExpiration;  // ms

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.access-expiration}") long accessExpiration,
                   @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("jwt.secret must be at least 256 bits (32 bytes) base64-encoded");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    // ====================================================================
    // 签发
    // ====================================================================

    /**
     * 生成 access token（24h 有效）。
     */
    public String generateAccessToken(Long userId, String userName, UserRole role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_USERNAME, userName)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 生成 refresh token（7d 有效）。
     */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpiration))
                .signWith(secretKey)
                .compact();
    }

    // ====================================================================
    // 校验
    // ====================================================================

    /**
     * 校验并解析 token。
     *
     * @param token    JWT 字符串
     * @param expectedType 期望的类型（access / refresh），null 则不校验类型
     * @return Claims
     * @throws ExpiredJwtException token 已过期
     * @throws JwtException        token 无效（签名错误、格式错误等）
     */
    public Claims validate(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (expectedType != null) {
            String type = claims.get(CLAIM_TYPE, String.class);
            if (!expectedType.equals(type)) {
                throw new JwtException("Invalid token type: " + type);
            }
        }
        return claims;
    }

    /**
     * 校验 access token。
     */
    public Claims validateAccessToken(String token) {
        return validate(token, TYPE_ACCESS);
    }

    /**
     * 校验 refresh token。
     */
    public Claims validateRefreshToken(String token) {
        return validate(token, TYPE_REFRESH);
    }

    // ====================================================================
    // 读取
    // ====================================================================

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public UserRole getRole(Claims claims) {
        return UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    public String getUserName(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    // ====================================================================
    // 工具
    // ====================================================================

    /**
     * Compute SHA-256 hex digest of a string.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
