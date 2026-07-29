package com.seckill.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder 独立配置 —— 与 SecurityConfig 分离，避免循环依赖。
 *
 * <p>UserServiceImpl 注入 PasswordEncoder，SecurityConfig 注入 JwtAuthFilter，<br>
 * 如果放在同一个配置类中会导致：SecurityConfig → JwtAuthFilter → UserService → PasswordEncoder → SecurityConfig 的循环。</p>
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }
}
