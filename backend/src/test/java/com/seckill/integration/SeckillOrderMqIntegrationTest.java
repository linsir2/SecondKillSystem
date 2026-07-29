package com.seckill.integration;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seckill.module.activity.mapper.ActivityMapper;
import com.seckill.module.activity.mapper.SeckillGoodsMapper;
import com.seckill.module.activity.model.entity.Activity;
import com.seckill.module.activity.model.entity.SeckillGoods;
import com.seckill.module.activity.model.enums.ActivityStatus;
import com.seckill.module.goods.mapper.GoodsMapper;
import com.seckill.module.goods.model.entity.Goods;
import com.seckill.module.order.mapper.MessageLogMapper;
import com.seckill.module.order.mapper.SeckillOrderMapper;
import com.seckill.module.order.model.entity.MessageLog;
import com.seckill.module.order.model.entity.SeckillOrder;
import com.seckill.module.order.model.enums.OrderStatus;
import com.seckill.module.order.model.enums.SendStatus;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 集成联调测试：从真实 RocketMQ 消息投递到 MySQL 订单入库。
 *
 * <p>覆盖全链路：RocketMQTemplate.syncSend → SeckillOrderConsumer.onMessage
 * → OrderService.createOrder(@Transactional) → seckill_order + message_log。
 *
 * <pre>
 * 分层策略：
 *   MySQL        真实（docker-compose, @BeforeEach 清表隔离）
 *   RocketMQ     真实（docker-compose, 无 @MockBean）
 *   Redis        不涉及（本测试不经过 Lua 扣减）
 * </pre>
 *
 * <p>注意：依赖 docker-compose 中 MySQL + RocketMQ 已启动。
 * RocketMQ 的 {@code autoCreateTopicEnable=true} 无需手动创建 topic。</p>
 *
 * <p>时序说明：RocketMQ consumer 在 Spring 启动后需要短暂时间完成注册。
 * {@code @BeforeAll warmupConsumerConnection} 发送一条预热消息并等其消费完毕，
 * 确保后续测试中 consumer 已就绪，poll 不超过 3s。</p>
 *
 * @see com.seckill.module.order.mq.SeckillOrderConsumer
 * @see com.seckill.module.order.service.OrderServiceImpl
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@DisplayName("Phase 2 集成联调: MQ → Consumer → MySQL 订单")
class SeckillOrderMqIntegrationTest {

    /** 预热：等待 consumer 连接就绪 */
    private static final int WARMUP_POLL_MS = 15_000;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private SeckillOrderMapper orderMapper;

    @Autowired
    private MessageLogMapper messageLogMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 秒杀商品 ID，必须与 insertTestData 一致 */
    private static final Long ACTIVITY_ID = 7001L;
    private static final Long GOODS_ID = 9001L;
    private static final Long SECKILL_GOODS_ID = 6001L;
    private static final Long MERCHANT_ID = 8001L;
    private static final Long USER_ID = 5001L;
    private static final BigDecimal SECKILL_PRICE = new BigDecimal("19.99");

    @BeforeAll
    void warmupConsumerConnection() {
        insertTestData();

        // 发一条预热消息等 consumer 注册完毕
        // 重试至多 5 次，应对 RocketMQ 刚启动时 topic 路由未就绪
        String warmupToken = UUID.randomUUID().toString();
        SeckillDeductedEvent warmupEvent = new SeckillDeductedEvent(
                warmupToken, USER_ID, ACTIVITY_ID, SECKILL_GOODS_ID, 1);

        RuntimeException lastEx = null;
        for (int i = 0; i < 5; i++) {
            try {
                rocketMQTemplate.syncSend("seckill_order:stock_deducted", warmupEvent);
                lastEx = null;
                break;
            } catch (RuntimeException e) {
                lastEx = e;
                sleep(2000);
            }
        }
        if (lastEx != null) throw lastEx;

        SeckillOrder order = pollByToken(warmupToken, WARMUP_POLL_MS);
        assertNotNull(order, "预热应成功，consumer 应在 " + WARMUP_POLL_MS + "ms 内就绪");

        cleanTables();
    }

    @BeforeEach
    void setUp() {
        cleanTables();
        insertTestData();
    }

    @AfterEach
    void tearDown() {
        cleanTables();
    }

    // ========================================================================
    // 测试数据准备
    // ========================================================================

    private void cleanTables() {
        orderMapper.delete(Wrappers.emptyWrapper());
        messageLogMapper.delete(Wrappers.emptyWrapper());
        seckillGoodsMapper.delete(Wrappers.emptyWrapper());
        activityMapper.delete(Wrappers.emptyWrapper());
        goodsMapper.delete(Wrappers.emptyWrapper());
        // wrapper.delete 在某些 MyBatis-Plus 版本可能不生效，显式 TRUNCATE 兜底
        jdbcTemplate.execute("DELETE FROM seckill_order");
        jdbcTemplate.execute("DELETE FROM message_log");
        jdbcTemplate.execute("DELETE FROM seckill_goods");
        jdbcTemplate.execute("DELETE FROM activity");
        jdbcTemplate.execute("DELETE FROM goods");
    }

    private void insertTestData() {
        Goods goods = new Goods();
        goods.setGoodsId(GOODS_ID);
        goods.setGoodsName("测试商品");
        goods.setMerchantId(MERCHANT_ID);
        goods.setPrice(new BigDecimal("99.99"));
        goods.setStatus(1);
        goods.setStock(100);
        goodsMapper.insert(goods);

        Activity activity = new Activity();
        activity.setActivityId(ACTIVITY_ID);
        activity.setActivityName("测试秒杀活动");
        activity.setMerchantId(MERCHANT_ID);
        activity.setStatus(ActivityStatus.running);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activityMapper.insert(activity);

        SeckillGoods sg = new SeckillGoods();
        sg.setSeckillGoodsId(SECKILL_GOODS_ID);
        sg.setActivityId(ACTIVITY_ID);
        sg.setGoodsId(GOODS_ID);
        sg.setSeckillPrice(SECKILL_PRICE);
        sg.setStock(100);
        sg.setLimitNum(10);
        seckillGoodsMapper.insert(sg);
    }

    // ========================================================================
    // G1 正常路径
    // ========================================================================

    @Nested
    @DisplayName("G1 正常路径")
    class NormalPath {

        @Test
        @DisplayName("P1 发 SeckillDeductedEvent → 订单创建成功，全字段校验")
        void normalCreate() {
            String orderToken = UUID.randomUUID().toString();
            SeckillDeductedEvent event = new SeckillDeductedEvent(
                    orderToken, USER_ID, ACTIVITY_ID, SECKILL_GOODS_ID, 2);

            rocketMQTemplate.syncSend("seckill_order:stock_deducted", event);

            // 轮询等 consumer 异步消费
            SeckillOrder order = pollByToken(orderToken, 5_000);
            assertNotNull(order, "订单应在 5s 内创建（warmup 后 consumer 已就绪）");

            // ———— order 全字段 ————
            assertEquals(orderToken, order.getOrderToken(), "orderToken");
            assertEquals(USER_ID, order.getUserId(), "userId");
            assertEquals(ACTIVITY_ID, order.getActivityId(), "activityId");
            assertEquals(SECKILL_GOODS_ID, order.getSeckillGoodsId(), "seckillGoodsId");
            assertEquals(2, order.getBuyCount(), "buyCount");
            assertEquals(0, SECKILL_PRICE.multiply(BigDecimal.valueOf(2))
                    .compareTo(order.getTotalAmount()), "totalAmount = 19.99 * 2");
            assertEquals(OrderStatus.UNPAID, order.getStatus(), "status");
            assertNotNull(order.getOrderNo(), "orderNo 由雪花算法生成");

            // ———— message_log ————
            MessageLog msgLog = messageLogMapper.selectOne(
                    new QueryWrapper<MessageLog>().eq("biz_id", orderToken));
            assertNotNull(msgLog, "message_log 应写一条");
            assertEquals("order_timeout", msgLog.getBizType());
            assertEquals("seckill_order", msgLog.getTopic());
            assertEquals("order_timeout", msgLog.getTag());
            assertEquals(SendStatus.INIT, msgLog.getStatus());
            assertEquals(0, msgLog.getRetryCount());
            assertNotNull(msgLog.getBody());
            assertTrue(msgLog.getBody().contains(orderToken), "body 含 orderToken");
            assertTrue(msgLog.getBody().contains("\"orderNo\":"), "body 含 orderNo");
        }
    }

    // ========================================================================
    // G2 幂等
    // ========================================================================

    @Nested
    @DisplayName("G2 幂等")
    class Idempotent {

        @Test
        @DisplayName("P2 同一消息发两次 → 只插 1 条 order + 1 条 message_log")
        void duplicateMessage() {
            String orderToken = UUID.randomUUID().toString();
            SeckillDeductedEvent event = new SeckillDeductedEvent(
                    orderToken, USER_ID, ACTIVITY_ID, SECKILL_GOODS_ID, 2);

            // 第 1 次
            rocketMQTemplate.syncSend("seckill_order:stock_deducted", event);
            assertNotNull(pollByToken(orderToken, 5_000), "第 1 次应创建");

            // 第 2 次（MQ 重试场景）
            rocketMQTemplate.syncSend("seckill_order:stock_deducted", event);
            sleep(1000);

            // order 只插 1 条
            List<SeckillOrder> orders = orderMapper.selectList(
                    new QueryWrapper<SeckillOrder>().eq("order_token", orderToken));
            assertEquals(1, orders.size());

            // message_log 也只插 1 条（DuplicateKeyException 时 return，不写 log）
            List<MessageLog> logs = messageLogMapper.selectList(
                    new QueryWrapper<MessageLog>().eq("biz_id", orderToken));
            assertEquals(1, logs.size());
        }
    }

    // ========================================================================
    // G3 非法消息
    // ========================================================================

    @Nested
    @DisplayName("G3 非法消息")
    class InvalidMessage {

        @Test
        @DisplayName("P3 buyCount=0 → 消费者 catch IAE，订单不创建")
        void invalidBuyCount() {
            String orderToken = UUID.randomUUID().toString();
            SeckillDeductedEvent event = new SeckillDeductedEvent(
                    orderToken, USER_ID, ACTIVITY_ID, SECKILL_GOODS_ID, 0);

            rocketMQTemplate.syncSend("seckill_order:stock_deducted", event);
            sleep(2000);

            SeckillOrder order = orderMapper.selectOne(
                    new QueryWrapper<SeckillOrder>().eq("order_token", orderToken));
            assertNull(order, "buyCount=0 不应创建订单");
        }
    }

    // ========================================================================
    // G4 同用户同商品幂等
    // ========================================================================

    @Nested
    @DisplayName("G4 同用户同商品")
    class SameUserGoods {

        @Test
        @DisplayName("P4 不同 token 但 uk_user_activity_goods 触发幂等")
        void sameUserGoodsDifferentToken() {
            String tokenA = UUID.randomUUID().toString();
            String tokenB = UUID.randomUUID().toString();
            SeckillDeductedEvent eventA = new SeckillDeductedEvent(
                    tokenA, USER_ID, ACTIVITY_ID, SECKILL_GOODS_ID, 1);
            SeckillDeductedEvent eventB = new SeckillDeductedEvent(
                    tokenB, USER_ID, ACTIVITY_ID, SECKILL_GOODS_ID, 1);

            // 第 1 条 → 创建
            rocketMQTemplate.syncSend("seckill_order:stock_deducted", eventA);
            assertNotNull(pollByToken(tokenA, 5_000), "第 1 条应创建成功");

            // 第 2 条 → uk_user_activity_goods 幂等
            rocketMQTemplate.syncSend("seckill_order:stock_deducted", eventB);
            sleep(1000);

            SeckillOrder orderB = orderMapper.selectOne(
                    new QueryWrapper<SeckillOrder>().eq("order_token", tokenB));
            assertNull(orderB, "uk_user_activity_goods 应阻止第 2 条");
        }
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    private SeckillOrder pollByToken(String orderToken, int maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            SeckillOrder order = orderMapper.selectOne(
                    new QueryWrapper<SeckillOrder>().eq("order_token", orderToken));
            if (order != null) return order;
            sleep(200);
        }
        return null;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
