package com.seckill.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / SpringDoc OpenAPI 配置。
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI seckillOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("秒杀系统 API")
                        .description("高并发、高可用秒杀架构练习项目 — REST API 文档")
                        .version("1.0.0")
                        .contact(new Contact().name("Seckill Team"))
                        .license(new License().name("Apache 2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("项目 README")
                        .url("https://github.com/linsir2/SecondKillSystem"));
    }
}
