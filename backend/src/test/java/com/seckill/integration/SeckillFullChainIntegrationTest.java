package com.seckill.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.constant.UserRole;
import com.seckill.common.result.Result;
import com.seckill.common.security.JwtUtil;
import com.seckill.common.security.SecurityContext;
import com.seckill.config.mq.SeckillProducerConfig;
import com.seckill.module.gateway.model.dto.SeckillRequest;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 1 集成联调测试：从 HTTP POST /api/v1/seckill/execute 到 MQ 消息发送。
 *
 * <p>覆盖全链路：JwtAuthFilter → GatewayCheckFilter → SeckillController
 * → SeckillStockService.deduct() → Redis Lua → MQ syncSend。
 *
 * <pre>
 * 分层策略：
 *   Redis        真实（docker-compose, FLUSHALL 隔离）
 *   RocketMQ     MockBean（验证调用 + 模拟异常）
 *   JWT          真实 jwtUtil.generateAccessToken()
 *   UserService  真实（JwtAuthFilter 构造需要，JWT 路径不调用）
 * </pre>
 *
 * @see com.seckill.filter.JwtAuthFilter
 * @see com.seckill.module.gateway.filter.GatewayCheckFilter
 * @see com.seckill.module.gateway.controller.SeckillController
 * @see com.seckill.module.stock.service.SeckillStockService
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
@DisplayName("Phase 1 集成联调: HTTP → MQ")
class SeckillFullChainIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    // ========================================================================
    // 常量
    // ========================================================================

    private static final Long ACTIVITY_ID = 100L;
    private static final Long GOODS_ID = 200L;
    private static final Long USER_A = 10001L;
    private static final Long USER_B = 10002L;
    private static final Long BLACKLISTED_USER = 99999L;

    private String jwtA;
    private String jwtB;

    private String stockKey;
    private String usersKey;
    private String limitKey;
    private String pendingKey;
    private static final String BLACKLIST_KEY = "seckill:blacklist";

    // ========================================================================
    // Setup
    // ========================================================================

    /**
     * 为指定用户清除 Redis 限流 key，用于同用户顺序发送多次请求的测试。
     */
    private void clearRateLimit(Long userId) {
        redisTemplate.delete("seckill:rate:" + userId);
    }

    @BeforeEach
    void setUp() {
        // 1. 清空 Redis
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });

        // 2. 重置 MQ mock
        Mockito.reset(rocketMQTemplate);

        // 3. 清除 SecurityContext（防止上游测试泄露）
        SecurityContext.clear();

        // 4. 生成 JWT
        jwtA = jwtUtil.generateAccessToken(USER_A, "UserA", UserRole.user);
        jwtB = jwtUtil.generateAccessToken(USER_B, "UserB", UserRole.user);

        // 5. 预计算 Redis key
        stockKey = "seckill:stock:" + ACTIVITY_ID + ":" + GOODS_ID;
        usersKey = "seckill:users:" + ACTIVITY_ID + ":" + GOODS_ID;
        limitKey = "seckill:limit:" + ACTIVITY_ID + ":" + GOODS_ID;
        pendingKey = "seckill:pending:" + ACTIVITY_ID;
    }

    /**
     * 预热 Redis 库存数据。
     */
    private void warmup(int stock, int limit) {
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        redisTemplate.opsForValue().set(limitKey, String.valueOf(limit));
    }

    /**
     * 构建带 JWT 认证的 HTTP 请求实体。
     */
    private HttpEntity<SeckillRequest> authRequest(String jwt, SeckillRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (jwt != null) {
            headers.setBearerAuth(jwt);
        }
        return new HttpEntity<>(body, headers);
    }

    /**
     * 发送 POST 请求。
     */
    private ResponseEntity<String> post(SeckillRequest body, String jwt) {
        return restTemplate.postForEntity(
                "/api/v1/seckill/execute",
                authRequest(jwt, body),
                String.class);
    }

    /**
     * 快速构建请求体。
     */
    private SeckillRequest req(Long activityId, Long goodsId, int buyCount) {
        SeckillRequest r = new SeckillRequest();
        r.setActivityId(activityId);
        r.setSeckillGoodsId(goodsId);
        r.setBuyCount(buyCount);
        return r;
    }

    /**
     * 解析响应 body 为 Result。
     */
    private Result<?> parseResult(ResponseEntity<String> response) throws Exception {
        return objectMapper.readValue(response.getBody(), Result.class);
    }

    /**
     * 从 Result 中提取 orderToken。
     */
    private String extractOrderToken(Result<?> result) {
        if (result.getData() instanceof Map<?, ?> data) {
            Object token = data.get("orderToken");
            return token != null ? token.toString() : null;
        }
        return null;
    }

    // ====================================================================
    // ====================================================================
    // G1 认证与网关
    // ====================================================================
    // ====================================================================

    @Nested
    @DisplayName("G1 认证与网关")
    class AuthAndGateway {

        @Test
        @DisplayName("A1 无认证头 → 401 + JSON body")
        void noAuth() throws Exception {
            SeckillRequest body = req(ACTIVITY_ID, GOODS_ID, 1);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/seckill/execute",
                    authRequest(null, body),
                    String.class);

            assertEquals(401, response.getStatusCode().value());
            assertNotNull(response.getBody());
            Result<?> result = parseResult(response);
            assertTrue(result.getMessage().contains("未登录"),
                    () -> "应提示未登录，实际: " + result.getMessage());
        }

        @Test
        @DisplayName("A2 无效 JWT → 401")
        void invalidJwt() throws Exception {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth("invalid.jwt.token");
            HttpEntity<SeckillRequest> entity = new HttpEntity<>(
                    req(ACTIVITY_ID, GOODS_ID, 1), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/seckill/execute", entity, String.class);

            assertEquals(401, response.getStatusCode().value());
            Result<?> result = parseResult(response);
            assertTrue(result.getMessage().contains("token无效") || result.getMessage().contains("已过期"),
                    () -> "应提示 token 无效，实际: " + result.getMessage());
        }

        @Test
        @DisplayName("A3 黑名单拦截 → 403")
        void blacklisted() throws Exception {
            // 预热黑名单
            redisTemplate.opsForSet().add(BLACKLIST_KEY, String.valueOf(BLACKLISTED_USER));
            String blacklistedJwt = jwtUtil.generateAccessToken(
                    BLACKLISTED_USER, "Banned", UserRole.user);

            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 1), blacklistedJwt);

            assertEquals(403, response.getStatusCode().value());
            Result<?> result = parseResult(response);
            assertTrue(result.getMessage().contains("封禁"),
                    () -> "应提示封禁，实际: " + result.getMessage());
        }

        @Test
        @DisplayName("A4 限流拦截 → 429")
        void rateLimited() throws Exception {
            warmup(10, 5);

            // 手动设置限流 key，模拟已被限流
            redisTemplate.opsForValue().set(
                    "seckill:rate:" + USER_A, "1", Duration.ofSeconds(2));

            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 1), jwtA);

            assertEquals(429, response.getStatusCode().value());
            Result<?> result = parseResult(response);
            assertTrue(result.getMessage().contains("频繁"),
                    () -> "应提示操作频繁，实际: " + result.getMessage());
        }

        @Test
        @DisplayName("A5 限流不阻塞其他用户 → 200")
        void rateLimitDoesNotAffectOtherUsers() throws Exception {
            warmup(10, 5);

            // 限制 USER_A
            redisTemplate.opsForValue().set(
                    "seckill:rate:" + USER_A, "1", Duration.ofSeconds(10));

            // USER_B 不受影响
            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 1), jwtB);

            assertEquals(200, response.getStatusCode().value());
            Result<?> result = parseResult(response);
            assertNotNull(extractOrderToken(result));
        }

        @Test
        @DisplayName("A6 限流 key 过期后恢复 → 200")
        void rateLimitRecovers() throws Exception {
            warmup(10, 5);

            // 手动设置限流 key，短 TTL
            redisTemplate.opsForValue().set(
                    "seckill:rate:" + USER_A, "1", Duration.ofMillis(100));

            // 等 key 过期
            Thread.sleep(200);

            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 1), jwtA);

            assertEquals(200, response.getStatusCode().value());
            Result<?> result = parseResult(response);
            assertNotNull(extractOrderToken(result));
        }
    }

    // ====================================================================
    // ====================================================================
    // G2 参数校验
    // ====================================================================
    // ====================================================================

    @Nested
    @DisplayName("G2 参数校验")
    class Validation {

        @Test
        @DisplayName("V1 activityId null → 400 或 500")
        void nullActivityId() {
            ResponseEntity<String> response = post(req(null, GOODS_ID, 1), jwtA);
            assertTrue(response.getStatusCode().value() == 400 || response.getStatusCode().value() == 500,
                    "参数校验失败应返回 4xx/5xx，实际: " + response.getStatusCode().value());
        }

        @Test
        @DisplayName("V2 seckillGoodsId null → 400 或 500")
        void nullGoodsId() {
            ResponseEntity<String> response = post(req(ACTIVITY_ID, null, 1), jwtA);
            assertTrue(response.getStatusCode().value() == 400 || response.getStatusCode().value() == 500);
        }

        @Test
        @DisplayName("V3 buyCount = 0 → 400 或 500")
        void zeroBuyCount() {
            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 0), jwtA);
            assertTrue(response.getStatusCode().value() == 400 || response.getStatusCode().value() == 500);
        }

        @Test
        @DisplayName("V4 buyCount < 0 → 400 或 500")
        void negativeBuyCount() {
            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, -1), jwtA);
            assertTrue(response.getStatusCode().value() == 400 || response.getStatusCode().value() == 500);
        }

        @Test
        @DisplayName("V5 buyCount null → 400 或 500")
        void nullBuyCount() {
            SeckillRequest body = new SeckillRequest();
            body.setActivityId(ACTIVITY_ID);
            body.setSeckillGoodsId(GOODS_ID);
            // buyCount 留 null

            ResponseEntity<String> response = post(body, jwtA);
            assertTrue(response.getStatusCode().value() == 400 || response.getStatusCode().value() == 500);
        }

        @Test
        @DisplayName("V6 空 body → 400 或 500")
        void emptyBody() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtA);
            HttpEntity<Object> entity = new HttpEntity<>("{}", headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/seckill/execute", entity, String.class);
            assertTrue(response.getStatusCode().value() == 400 || response.getStatusCode().value() == 500);
        }
    }

    // ====================================================================
    // ====================================================================
    // G3 业务逻辑
    // ====================================================================
    // ====================================================================

    @Nested
    @DisplayName("G3 业务逻辑")
    class BusinessLogic {

        @Test
        @DisplayName("B1 正常抢购 → 200 + orderToken")
        void normalPurchase() throws Exception {
            warmup(10, 5);

            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 2), jwtA);

            assertEquals(200, response.getStatusCode().value());
            Result<?> result = parseResult(response);
            String token = extractOrderToken(result);
            assertNotNull(token, "应有 orderToken");
            assertFalse(token.isBlank());

            // Redis 验证
            assertEquals("8", redisTemplate.opsForValue().get(stockKey), "stock 应扣减 2");
            assertTrue(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A)));
            assertNotNull(redisTemplate.opsForZSet().score(pendingKey, token));
        }

        @Test
        @DisplayName("B2 售罄 → 400")
        void soldOut() throws Exception {
            warmup(0, 5);

            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 1), jwtA);

            assertEquals(400, response.getStatusCode().value());
            Result<?> result = parseResult(response);
            assertTrue(result.getMessage().contains("售罄"),
                    () -> "应提示售罄，实际: " + result.getMessage());
        }

        @Test
        @DisplayName("B3 重复购买 → 200 → 400")
        void duplicatePurchase() throws Exception {
            warmup(10, 5);

            // 第 1 次 → 成功
            ResponseEntity<String> r1 = post(req(ACTIVITY_ID, GOODS_ID, 2), jwtA);
            assertEquals(200, r1.getStatusCode().value());

            // 清除限流 key（否则第 2 次会被 1s 限流拦截，走不到重复检测）
            clearRateLimit(USER_A);

            // 第 2 次 → 重复
            ResponseEntity<String> r2 = post(req(ACTIVITY_ID, GOODS_ID, 1), jwtA);
            assertEquals(400, r2.getStatusCode().value());
            Result<?> result2 = parseResult(r2);
            assertTrue(result2.getMessage().contains("重复购买"),
                    () -> "应提示重复，实际: " + result2.getMessage());

            // Redis：库存只扣第 1 次的量
            assertEquals("8", redisTemplate.opsForValue().get(stockKey));
            assertEquals(1L, redisTemplate.opsForSet().size(usersKey));
        }

        @Test
        @DisplayName("B4 超限购 → 400")
        void overLimit() throws Exception {
            warmup(10, 3);

            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 5), jwtA);

            assertEquals(400, response.getStatusCode().value());
            Result<?> result = parseResult(response);
            assertTrue(result.getMessage().contains("限购"),
                    () -> "应提示限购，实际: " + result.getMessage());

            // Redis 无副作用
            assertEquals("10", redisTemplate.opsForValue().get(stockKey));
            assertEquals(0L, redisTemplate.opsForSet().size(usersKey));
        }

        @Test
        @DisplayName("B5 未预热（无 stock key）→ 售罄")
        void notPreheated() throws Exception {
            // 只设 limit，不设 stock
            redisTemplate.opsForValue().set(limitKey, "5");

            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 1), jwtA);

            assertEquals(400, response.getStatusCode().value());
            Result<?> result = parseResult(response);
            assertTrue(result.getMessage().contains("售罄"),
                    () -> "应提示售罄，实际: " + result.getMessage());

            // 不产生垃圾 key
            assertNull(redisTemplate.opsForValue().get(stockKey));
            assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(usersKey)));
        }

        @Test
        @DisplayName("B6 最后一件 → A成功 B售罄")
        void lastItem() throws Exception {
            warmup(1, 5);

            // A 成功
            ResponseEntity<String> r1 = post(req(ACTIVITY_ID, GOODS_ID, 1), jwtA);
            assertEquals(200, r1.getStatusCode().value());

            // B 售罄
            ResponseEntity<String> r2 = post(req(ACTIVITY_ID, GOODS_ID, 1), jwtB);
            assertEquals(400, r2.getStatusCode().value());
            Result<?> result2 = parseResult(r2);
            assertTrue(result2.getMessage().contains("售罄"));

            assertEquals("0", redisTemplate.opsForValue().get(stockKey));
            assertEquals(1L, redisTemplate.opsForSet().size(usersKey));
        }

        @Test
        @DisplayName("B7 多人独立抢全部成功")
        void allIndependentSuccess() throws Exception {
            warmup(5, 3);
            int count = 5;
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String jwt = jwtUtil.generateAccessToken(20000L + i, "User" + i, UserRole.user);
                ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 1), jwt);
                assertEquals(200, response.getStatusCode().value(),
                        "用户 " + (20000L + i) + " 应成功");
                tokens.add(extractOrderToken(parseResult(response)));
            }

            assertEquals(count, tokens.stream().distinct().count(), "所有 orderToken 应不同");
            assertEquals("0", redisTemplate.opsForValue().get(stockKey));
            assertEquals(count, redisTemplate.opsForSet().size(usersKey));
        }
    }

    // ====================================================================
    // ====================================================================
    // G4 MQ 可靠性
    // ====================================================================
    // ====================================================================

    @Nested
    @DisplayName("G4 MQ 可靠性")
    class MqReliability {

        @Test
        @DisplayName("M1 正常发送 → syncSend 被调用")
        void mqSentOnSuccess() throws Exception {
            warmup(10, 5);

            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 2), jwtA);

            assertEquals(200, response.getStatusCode().value());

            verify(rocketMQTemplate, times(1))
                    .syncSend(eq(SeckillProducerConfig.DESTINATION_STOCK_DEDUCTED),
                            any(Object.class));
        }

        @Test
        @DisplayName("M2 事件体字段完整匹配")
        void eventFieldsMatch() throws Exception {
            warmup(10, 5);

            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 2), jwtA);
            assertEquals(200, response.getStatusCode().value());
            Result<?> result = parseResult(response);
            String orderToken = extractOrderToken(result);

            ArgumentCaptor<SeckillDeductedEvent> eventCaptor =
                    ArgumentCaptor.forClass(SeckillDeductedEvent.class);
            verify(rocketMQTemplate).syncSend(anyString(), eventCaptor.capture());

            SeckillDeductedEvent event = eventCaptor.getValue();
            assertEquals(orderToken, event.orderToken());
            assertEquals(USER_A, event.userId());
            assertEquals(ACTIVITY_ID, event.activityId());
            assertEquals(GOODS_ID, event.seckillGoodsId());
            assertEquals(2, event.buyCount());
        }

        @Test
        @DisplayName("M3 syncSend 异常 → 补偿 + 400")
        void mqSyncException() throws Exception {
            warmup(10, 5);

            doThrow(new RuntimeException("Broker unavailable"))
                    .when(rocketMQTemplate)
                    .syncSend(anyString(), any(Object.class));

            ResponseEntity<String> response = post(req(ACTIVITY_ID, GOODS_ID, 2), jwtA);

            assertEquals(400, response.getStatusCode().value());
            Result<?> result = parseResult(response);
            assertTrue(result.getMessage().contains("系统繁忙"),
                    () -> "应提示系统繁忙，实际: " + result.getMessage());

            // Redis 已补偿（stock 恢复）
            assertEquals("10", redisTemplate.opsForValue().get(stockKey));
            assertFalse(Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A))));
        }

        @Test
        @DisplayName("M5 补偿后可重买")
        void canReBuyAfterCompensate() throws Exception {
            warmup(10, 5);

            // 第 1 次 → MQ 失败 → 补偿
            doThrow(new RuntimeException("Broker unavailable"))
                    .when(rocketMQTemplate)
                    .syncSend(anyString(), any(Object.class));

            ResponseEntity<String> r1 = post(req(ACTIVITY_ID, GOODS_ID, 2), jwtA);
            assertEquals(400, r1.getStatusCode().value());

            // 第 2 次 → 恢复 MQ → 应成功
            Mockito.reset(rocketMQTemplate);
            clearRateLimit(USER_A);

            ResponseEntity<String> r2 = post(req(ACTIVITY_ID, GOODS_ID, 2), jwtA);
            assertEquals(200, r2.getStatusCode().value());

            Result<?> result2 = parseResult(r2);
            assertNotNull(extractOrderToken(result2));

            assertEquals("8", redisTemplate.opsForValue().get(stockKey), "stock 应扣 2（10-2=8）");
            assertTrue(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A)));
        }

        @Test
        @DisplayName("M5 重复购买 → syncSend 只调 1 次")
        void mqNotCalledOnDuplicate() throws Exception {
            warmup(10, 5);

            // 第 1 次 → 成功 → MQ 发送
            post(req(ACTIVITY_ID, GOODS_ID, 2), jwtA);
            verify(rocketMQTemplate, times(1))
                    .syncSend(anyString(), any(Object.class));

            // 第 2 次 → 重复 → MQ 不应再发送
            post(req(ACTIVITY_ID, GOODS_ID, 1), jwtA);
            verify(rocketMQTemplate, times(1))
                    .syncSend(anyString(), any(Object.class));
        }
    }

    // ====================================================================
    // ====================================================================
    // G5 高并发
    // ====================================================================
    // ====================================================================

    @Nested
    @DisplayName("G5 高并发")
    class HighConcurrency {

        @Test
        @DisplayName("C1 30 线程抢 5 件 → 5 成功 25 售罄")
        void concurrentOversell() throws Exception {
            warmup(5, 3);
            int threadCount = 30;

            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tokens.add(jwtUtil.generateAccessToken(
                        30000L + i, "ConUser" + i, UserRole.user));
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            List<java.util.concurrent.Future<ResponseEntity<String>>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final String token = tokens.get(i);
                futures.add(executor.submit(() -> {
                    latch.await();
                    return post(req(ACTIVITY_ID, GOODS_ID, 1), token);
                }));
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS),
                    "并发测试超时");

            int success = 0, soldOut = 0;
            for (var f : futures) {
                ResponseEntity<String> resp = f.get();
                if (resp.getStatusCode().value() == 200) {
                    success++;
                } else {
                    Result<?> r = objectMapper.readValue(resp.getBody(), Result.class);
                    if (r.getMessage().contains("售罄")) soldOut++;
                }
            }

            assertEquals(5, success, "恰好 5 人成功");
            assertEquals(25, soldOut, "其余 25 人售罄");

            // Redis 验证
            assertEquals("0", redisTemplate.opsForValue().get(stockKey));
            assertEquals(5L, redisTemplate.opsForSet().size(usersKey));
            assertEquals(5L, redisTemplate.opsForZSet().size(pendingKey));
        }

        @Test
        @DisplayName("C2 20 线程同用户 → 1 成功 19 重复")
        void concurrentSameUser() throws Exception {
            warmup(5, 3);
            int threadCount = 20;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            List<java.util.concurrent.Future<ResponseEntity<String>>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    latch.await();
                    return post(req(ACTIVITY_ID, GOODS_ID, 1), jwtA);
                }));
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS),
                    "并发测试超时");

            int success = 0, rateLimited = 0;
            for (var f : futures) {
                ResponseEntity<String> resp = f.get();
                if (resp.getStatusCode().value() == 200) {
                    success++;
                } else if (resp.getStatusCode().value() == 429) {
                    rateLimited++;
                }
            }

            assertEquals(1, success, "速率限制：20 同用户并发，只能 1 次成功");
            assertEquals(threadCount - 1, rateLimited,
                    "其余 19 次被 1s 限流拦截（业务层的重复检测通过单元测试 S7 覆盖）");

            assertEquals("4", redisTemplate.opsForValue().get(stockKey), "stock 只扣 1");
            assertEquals(1L, redisTemplate.opsForSet().size(usersKey));
            assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey));
        }

        @Test
        @DisplayName("C3 10 线程全部超限购 → 全 400，stock 不变")
        void concurrentAllOverLimit() throws Exception {
            warmup(10, 2);
            int threadCount = 10;

            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tokens.add(jwtUtil.generateAccessToken(
                        40000L + i, "OLUser" + i, UserRole.user));
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            List<java.util.concurrent.Future<ResponseEntity<String>>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final String token = tokens.get(i);
                futures.add(executor.submit(() -> {
                    latch.await();
                    return post(req(ACTIVITY_ID, GOODS_ID, 5), token); // limit=2, buy=5
                }));
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS),
                    "并发测试超时");

            int overLimit = 0;
            for (var f : futures) {
                ResponseEntity<String> resp = f.get();
                assertEquals(400, resp.getStatusCode().value(), "全部应 400");
                Result<?> r = objectMapper.readValue(resp.getBody(), Result.class);
                if (r.getMessage().contains("限购")) overLimit++;
            }

            assertEquals(threadCount, overLimit, "全部超限购");

            // Redis 完全无副作用
            assertEquals("10", redisTemplate.opsForValue().get(stockKey));
            assertEquals(0L, redisTemplate.opsForSet().size(usersKey));
            assertEquals(0L, redisTemplate.opsForZSet().size(pendingKey));
        }

        @Test
        @DisplayName("C4 50 线程抢最后 1 件 → 1 成功 49 售罄")
        void concurrentLastItem() throws Exception {
            warmup(1, 3);
            int threadCount = 50;

            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tokens.add(jwtUtil.generateAccessToken(
                        50000L + i, "LastUser" + i, UserRole.user));
            }

            ExecutorService executor = Executors.newFixedThreadPool(
                    Math.min(threadCount, 32));
            CountDownLatch latch = new CountDownLatch(1);
            List<java.util.concurrent.Future<ResponseEntity<String>>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final String token = tokens.get(i);
                futures.add(executor.submit(() -> {
                    latch.await();
                    return post(req(ACTIVITY_ID, GOODS_ID, 1), token);
                }));
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS),
                    "并发测试超时");

            int success = 0, soldOut = 0;
            for (var f : futures) {
                ResponseEntity<String> resp = f.get();
                if (resp.getStatusCode().value() == 200) {
                    success++;
                } else {
                    Result<?> r = objectMapper.readValue(resp.getBody(), Result.class);
                    if (r.getMessage().contains("售罄")) soldOut++;
                }
            }

            assertEquals(1, success, "恰好 1 人成功");
            assertEquals(threadCount - 1, soldOut, "其余 49 人售罄");

            assertEquals("0", redisTemplate.opsForValue().get(stockKey));
            assertEquals(1L, redisTemplate.opsForSet().size(usersKey));
            assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey));
        }
    }
}
