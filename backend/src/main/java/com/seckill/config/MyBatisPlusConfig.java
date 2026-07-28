package com.seckill.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MyBatis-Plus 配置。
 * 分页插件等扩展拦截器按需在此添加。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class MyBatisPlusConfig {
}
