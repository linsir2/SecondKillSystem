package com.seckill.config.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局 Jackson 配置：将所有 Long/long 类型序列化为字符串，避免前端 JavaScript
 * 因雪花 ID 超出安全整数范围（2^53-1）导致精度丢失。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule longToStringModule() {
        SimpleModule module = new SimpleModule("long-to-string");
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(long.class, ToStringSerializer.instance);
        return module;
    }
}
