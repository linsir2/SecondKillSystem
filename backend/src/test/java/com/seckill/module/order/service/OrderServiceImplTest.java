package com.seckill.module.order.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.activity.mapper.SeckillGoodsMapper;
import com.seckill.module.activity.model.entity.SeckillGoods;
import com.seckill.module.order.mapper.MessageLogMapper;
import com.seckill.module.order.mapper.SeckillOrderMapper;
import com.seckill.module.order.model.entity.MessageLog;
import com.seckill.module.order.model.entity.SeckillOrder;
import com.seckill.module.order.model.enums.OrderStatus;
import com.seckill.module.order.model.enums.SendStatus;
import com.seckill.module.order.model.dto.OrderCancelledEvent;
import com.seckill.module.order.model.dto.OrderPaidEvent;
import com.seckill.module.order.model.dto.OrderTimedOutEvent;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OrderServiceImpl.createOrder — 消费 MQ 创建订单，纯 Mockito 单元测试（无 Spring 上下文）。
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │ 一 参数校验  V1-V5    event null / orderToken blank / id null / buyCount≤0 │
 * │ 二 正常创建  H1-H3    buyCount 2/1, price 0.01 精度                         │
 * │ 三 订单字段  F1       order 全字段 ArgumentCaptor                           │
 * │ 四 消息字段  M1       message_log 全字段 ArgumentCaptor                     │
 * │ 五 幂等     I1-I2    单次 DKE / 连续 3 次重试                              │
 * │ 六 不存在   D1       seckillGoods 查不到                                    │
 * │ 七 事务异常 T1       message_log 插入失败                                    │
 * │ 八 并发     C1-C2    两线程相同 token / 不同 token 同用户+商品               │
 * └──────────────────────────────────────────────────────────────────────────┘
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl — createOrder")
class OrderServiceImplTest {

    @Mock private SeckillOrderMapper orderMapper;
    @Mock private MessageLogMapper messageLogMapper;
    @Mock private SeckillGoodsMapper seckillGoodsMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OrderService orderService;

    private static final Long ACTIVITY_ID = 100L;
    private static final Long GOODS_ID    = 200L;
    private static final Long USER_A      = 10001L;
    private static final String ORDER_TOKEN = "test-uuid-order-token";
    private static final BigDecimal SECKILL_PRICE = new BigDecimal("19.99");

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderMapper, messageLogMapper, seckillGoodsMapper, eventPublisher);
    }

    // ============ helpers ============

    private SeckillDeductedEvent event(int buyCount) {
        return new SeckillDeductedEvent(ORDER_TOKEN, USER_A, ACTIVITY_ID, GOODS_ID, buyCount);
    }

    private SeckillGoods mockGoods() {
        var sg = new SeckillGoods();
        sg.setSeckillGoodsId(GOODS_ID);
        sg.setActivityId(ACTIVITY_ID);
        sg.setSeckillPrice(SECKILL_PRICE);
        return sg;
    }

    private void assertIAE(Executable r, String msg) {
        var ex = assertThrows(IllegalArgumentException.class, r);
        assertTrue(ex.getMessage().toLowerCase().contains(msg.toLowerCase()),
                () -> "IAE 应包含「" + msg + "」，实际: " + ex.getMessage());
    }

    /** 捕获 SeckillOrder insert 参数，返回 captor 便于链式断言。 */
    private ArgumentCaptor<SeckillOrder> captureOrder() {
        var c = ArgumentCaptor.forClass(SeckillOrder.class);
        verify(orderMapper).insert(c.capture());
        return c;
    }

    /** 捕获 MessageLog insert 参数。 */
    private ArgumentCaptor<MessageLog> captureMessageLog() {
        var c = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogMapper).insert(c.capture());
        return c;
    }

    // ================================================================
    // 一、参数校验 V1-V5
    // ================================================================

    @Test
    @DisplayName("V1 event == null -> IAE")
    void nullEvent() {
        assertIAE(() -> orderService.createOrder(null), "event");
    }

    @Test
    @DisplayName("V2 orderToken null / blank -> IAE")
    void blankOrderToken() {
        assertIAE(() -> orderService.createOrder(
                new SeckillDeductedEvent(null, USER_A, ACTIVITY_ID, GOODS_ID, 2)), "orderToken");
        assertIAE(() -> orderService.createOrder(
                new SeckillDeductedEvent("", USER_A, ACTIVITY_ID, GOODS_ID, 2)), "orderToken");
        assertIAE(() -> orderService.createOrder(
                new SeckillDeductedEvent("  ", USER_A, ACTIVITY_ID, GOODS_ID, 2)), "orderToken");
    }

    @Test
    @DisplayName("V3 userId / activityId / seckillGoodsId null -> IAE")
    void nullIds() {
        assertIAE(() -> orderService.createOrder(
                new SeckillDeductedEvent(ORDER_TOKEN, null, ACTIVITY_ID, GOODS_ID, 2)), "userId");
        assertIAE(() -> orderService.createOrder(
                new SeckillDeductedEvent(ORDER_TOKEN, USER_A, null, GOODS_ID, 2)), "activityId");
        assertIAE(() -> orderService.createOrder(
                new SeckillDeductedEvent(ORDER_TOKEN, USER_A, ACTIVITY_ID, null, 2)), "seckillGoodsId");
    }

    @Test
    @DisplayName("V4 buyCount == 0 -> IAE")
    void zeroBuyCount() {
        assertIAE(() -> orderService.createOrder(event(0)), "buyCount");
    }

    @Test
    @DisplayName("V5 buyCount < 0 -> IAE")
    void negativeBuyCount() {
        assertIAE(() -> orderService.createOrder(event(-1)), "buyCount");
        assertIAE(() -> orderService.createOrder(event(-99)), "buyCount");
    }

    // ================================================================
    // 二、正常创建 H1-H3
    // ================================================================

    @Test
    @DisplayName("H1 buyCount=2, price=19.99 -> totalAmount=39.98, order+msg 各插 1 次")
    void normalCreate() {
        when(seckillGoodsMapper.selectById(GOODS_ID)).thenReturn(mockGoods());
        orderService.createOrder(event(2));

        var o = captureOrder().getValue();
        assertEquals(ORDER_TOKEN, o.getOrderToken());
        assertEquals(2, o.getBuyCount());
        assertEquals(0, new BigDecimal("39.98").compareTo(o.getTotalAmount()));
        assertEquals(OrderStatus.UNPAID, o.getStatus());

        verify(messageLogMapper).insert(any(MessageLog.class));
    }

    @Test
    @DisplayName("H2 buyCount=1 (最小值) -> totalAmount=19.99")
    void minBuyCount() {
        when(seckillGoodsMapper.selectById(GOODS_ID)).thenReturn(mockGoods());
        orderService.createOrder(event(1));

        assertEquals(0, new BigDecimal("19.99").compareTo(captureOrder().getValue().getTotalAmount()));
    }

    @Test
    @DisplayName("H3 price=0.01, buyCount=1 -> totalAmount=0.01, scale=2")
    void precisionEdge() {
        var sg = mockGoods();
        sg.setSeckillPrice(new BigDecimal("0.01"));
        when(seckillGoodsMapper.selectById(GOODS_ID)).thenReturn(sg);

        orderService.createOrder(event(1));
        var total = captureOrder().getValue().getTotalAmount();
        assertEquals(0, new BigDecimal("0.01").compareTo(total));
        assertEquals(2, total.scale());
    }

    // ================================================================
    // 三、order 全字段 F1
    // ================================================================

    @Test
    @DisplayName("F1 order 全字段 — ArgumentCaptor")
    void orderFields() {
        when(seckillGoodsMapper.selectById(GOODS_ID)).thenReturn(mockGoods());
        orderService.createOrder(event(2));

        var o = captureOrder().getValue();
        // orderNo 由 MyBatis-Plus ASSIGN_ID 在 insert 实现层生成，Mockito mock 不执行该逻辑
        // 此处不 assert orderNo，留待集成测试覆盖
        assertEquals(ORDER_TOKEN,   o.getOrderToken(),     "orderToken 匹配");
        assertEquals(USER_A,        o.getUserId(),          "userId");
        assertEquals(ACTIVITY_ID,   o.getActivityId(),      "activityId");
        assertEquals(GOODS_ID,      o.getSeckillGoodsId(),  "seckillGoodsId");
        assertEquals(2,             o.getBuyCount());
        assertEquals(new BigDecimal("39.98"), o.getTotalAmount());
        assertEquals(OrderStatus.UNPAID,    o.getStatus(),  "默认 UNPAID");
        assertNull(o.getPayTime(),  "UNPAID 时 payTime 为 null");
        assertNull(o.getCancelTime(), "UNPAID 时 cancelTime 为 null");
    }

    // ================================================================
    // 四、message_log 全字段 M1
    // ================================================================

    @Test
    @DisplayName("M1 message_log 全字段 — ArgumentCaptor")
    void messageLogFields() {
        when(seckillGoodsMapper.selectById(GOODS_ID)).thenReturn(mockGoods());
        orderService.createOrder(event(2));

        var log = captureMessageLog().getValue();

        assertEquals("order_timeout",        log.getBizType());
        assertEquals(ORDER_TOKEN,            log.getBizId());
        assertEquals("seckill_order",        log.getTopic());
        assertEquals("order_timeout",        log.getTag());
        assertEquals(SendStatus.INIT,        log.getStatus());
        assertEquals(0,                      log.getRetryCount());
        assertNull(log.getSendTime());
        assertNotNull(log.getBody());
        assertTrue(log.getBody().contains(ORDER_TOKEN), "body JSON 应包含 orderToken");
        assertTrue(log.getBody().contains("orderNo"),    "body JSON 应包含 orderNo");
    }

    // ================================================================
    // 五、幂等 I1-I2
    // ================================================================

    @Test
    @DisplayName("I1 orderToken 重复 -> DuplicateKeyException 静默返回, msg 不插")
    void idempotentDuplicateToken() {
        when(seckillGoodsMapper.selectById(GOODS_ID)).thenReturn(mockGoods());
        doThrow(new DuplicateKeyException("UK order_token"))
                .when(orderMapper).insert(any(SeckillOrder.class));

        assertDoesNotThrow(() -> orderService.createOrder(event(2)));
        verify(orderMapper, times(1)).insert(any(SeckillOrder.class));
        verify(messageLogMapper, never()).insert(any(MessageLog.class));
    }

    @Test
    @DisplayName("I2 连续 3 次重试 -> 始终返回成功, msg 只插 1 次")
    void idempotentTripleRetry() {
        when(seckillGoodsMapper.selectById(GOODS_ID)).thenReturn(mockGoods());

        // 第 1 次 -> 成功
        orderService.createOrder(event(2));
        verify(messageLogMapper, times(1)).insert(any(MessageLog.class));

        // 第 2 次 -> DKE（模拟 MQ 重试）
        doThrow(new DuplicateKeyException("UK"))
                .when(orderMapper).insert(any(SeckillOrder.class));
        assertDoesNotThrow(() -> orderService.createOrder(event(2)));
        verify(messageLogMapper, times(1)).insert(any(MessageLog.class));

        // 第 3 次 -> DKE
        assertDoesNotThrow(() -> orderService.createOrder(event(2)));
        verify(messageLogMapper, times(1)).insert(any(MessageLog.class));

        verify(orderMapper, times(3)).insert(any(SeckillOrder.class));
    }

    // ================================================================
    // 六、秒杀商品不存在 D1
    // ================================================================

    @Test
    @DisplayName("D1 seckillGoods 查不到 -> BusinessException, order/msg 无交互")
    void seckillGoodsNotFound() {
        when(seckillGoodsMapper.selectById(GOODS_ID)).thenReturn(null);

        var ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(event(2)));
        assertTrue(ex.getMessage().contains("秒杀商品不存在"));
        verifyNoInteractions(orderMapper, messageLogMapper);
    }

    // ================================================================
    // 七、事务异常 T1
    // ================================================================

    @Test
    @DisplayName("T1 messageLog 插入抛异常 -> RuntimeException 传播")
    void messageLogInsertFailure() {
        when(seckillGoodsMapper.selectById(GOODS_ID)).thenReturn(mockGoods());
        doThrow(new RuntimeException("DB error"))
                .when(messageLogMapper).insert(any(MessageLog.class));

        assertThrows(RuntimeException.class, () -> orderService.createOrder(event(2)));
        verify(orderMapper).insert(any(SeckillOrder.class));
        verify(messageLogMapper).insert(any(MessageLog.class));
    }

    // ================================================================
    // 八、并发 C1-C2
    // ================================================================

    @Test
    @DisplayName("C1 两线程相同 orderToken -> 各自不抛, msg 只插 1 次")
    void concurrentSameToken() throws Exception {
        when(seckillGoodsMapper.selectById(GOODS_ID)).thenReturn(mockGoods());

        var first = new AtomicBoolean(true);
        doAnswer(inv -> {
            if (first.compareAndSet(true, false)) return 0;
            throw new DuplicateKeyException("UK order_token");
        }).when(orderMapper).insert(any(SeckillOrder.class));

        var latch = new CountDownLatch(2);
        var errors = new AtomicInteger(0);
        var evt = event(2);

        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try { orderService.createOrder(evt); }
                catch (Exception e) { errors.incrementAndGet(); }
                finally { latch.countDown(); }
            }).start();
        }
        latch.await();

        assertEquals(0, errors.get(), "两线程都不应抛异常");
        verify(orderMapper, times(2)).insert(any(SeckillOrder.class));
        verify(messageLogMapper, times(1)).insert(any(MessageLog.class));
    }

    @Test
    @DisplayName("C2 两线程, 不同 token 同用户+商品 -> uk_user_activity_goods 兜底")
    void concurrentSameUserGoods() throws Exception {
        when(seckillGoodsMapper.selectById(GOODS_ID)).thenReturn(mockGoods());

        var first = new AtomicBoolean(true);
        doAnswer(inv -> {
            if (first.compareAndSet(true, false)) return 0;
            throw new DuplicateKeyException("UK user_activity_goods");
        }).when(orderMapper).insert(any(SeckillOrder.class));

        var latch = new CountDownLatch(2);
        var errors = new AtomicInteger(0);
        var evt1 = new SeckillDeductedEvent("token-A", USER_A, ACTIVITY_ID, GOODS_ID, 2);
        var evt2 = new SeckillDeductedEvent("token-B", USER_A, ACTIVITY_ID, GOODS_ID, 2);

        new Thread(() -> {
            try { orderService.createOrder(evt1); }
            catch (Exception e) { errors.incrementAndGet(); }
            finally { latch.countDown(); }
        }).start();
        new Thread(() -> {
            try { orderService.createOrder(evt2); }
            catch (Exception e) { errors.incrementAndGet(); }
            finally { latch.countDown(); }
        }).start();
        latch.await();

        assertEquals(0, errors.get());
        verify(orderMapper, times(2)).insert(any(SeckillOrder.class));
        verify(messageLogMapper, times(1)).insert(any(MessageLog.class));
    }

    // ========================================================================
    // 九、支付 P1-P11  — helpers
    // ========================================================================

    private static final Long ORDER_NO = 9001L;

    private SeckillOrder orderWithStatus(OrderStatus status) {
        var o = new SeckillOrder();
        o.setOrderNo(ORDER_NO);
        o.setOrderToken(ORDER_TOKEN);
        o.setUserId(USER_A);
        o.setActivityId(ACTIVITY_ID);
        o.setSeckillGoodsId(GOODS_ID);
        o.setBuyCount(2);
        o.setTotalAmount(new BigDecimal("39.98"));
        o.setStatus(status);
        return o;
    }

    // ========================================================================
    // 九、支付 P1-P11
    // ========================================================================

    @Test
    @DisplayName("P0 userId null -> IAE")
    void payNullUserId() {
        assertIAE(() -> orderService.pay(ORDER_NO, null), "userId");
    }

    @Test
    @DisplayName("P0.5 userId 与订单不匹配 -> BusinessException")
    void payUserIdMismatch() {
        var o = orderWithStatus(OrderStatus.UNPAID);
        o.setUserId(99999L); // 另一个用户
        when(orderMapper.selectById(ORDER_NO)).thenReturn(o);

        var ex = assertThrows(BusinessException.class, () -> orderService.pay(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("无权支付"));
        verify(orderMapper, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("P1 orderNo null -> IAE")
    void payNullOrderNo() {
        assertIAE(() -> orderService.pay(null, USER_A), "orderNo");
    }

    @Test
    @DisplayName("P2 UNPAID -> PAID, payTime 设置, update 被调用, 发布 OrderPaidEvent(含 activityId)")
    void paySuccess() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(orderWithStatus(OrderStatus.UNPAID));
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(1);

        orderService.pay(ORDER_NO, USER_A);

        var captor = ArgumentCaptor.forClass(SeckillOrder.class);
        verify(orderMapper).update(captor.capture(), any());
        assertEquals(OrderStatus.PAID, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getPayTime());

        var eventCaptor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(ACTIVITY_ID, eventCaptor.getValue().activityId());
        assertEquals(ORDER_TOKEN, eventCaptor.getValue().orderToken());
        assertEquals(ORDER_NO, eventCaptor.getValue().orderNo());
    }

    @Test
    @DisplayName("P3 order 不存在 -> BusinessException, update/event 未调用")
    void payOrderNotFound() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(null);

        var ex = assertThrows(BusinessException.class, () -> orderService.pay(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("不存在"));
        verify(orderMapper, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("P4 已 PAID -> BusinessException, update/event 未调用")
    void payAlreadyPaid() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(orderWithStatus(OrderStatus.PAID));

        var ex = assertThrows(BusinessException.class, () -> orderService.pay(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("只能支付待支付订单"));
        verify(orderMapper, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("P5 已 CANCELLED -> BusinessException, update/event 未调用")
    void payAlreadyCancelled() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(orderWithStatus(OrderStatus.CANCELLED));

        var ex = assertThrows(BusinessException.class, () -> orderService.pay(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("只能支付待支付订单"));
        verify(orderMapper, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("P6 乐观锁冲突, 重查为 PAID -> BusinessException, event 未发布")
    void payOptLockConflictThenPaid() {
        when(orderMapper.selectById(ORDER_NO))
                .thenReturn(orderWithStatus(OrderStatus.UNPAID))
                .thenReturn(orderWithStatus(OrderStatus.PAID));
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(0);

        var ex = assertThrows(BusinessException.class, () -> orderService.pay(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("已支付"));
        verify(orderMapper, times(2)).selectById(ORDER_NO);
        verify(orderMapper).update(any(SeckillOrder.class), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("P7 乐观锁冲突, 重查为 CANCELLED -> BusinessException, event 未发布")
    void payOptLockConflictThenCancelled() {
        when(orderMapper.selectById(ORDER_NO))
                .thenReturn(orderWithStatus(OrderStatus.UNPAID))
                .thenReturn(orderWithStatus(OrderStatus.CANCELLED));
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(0);

        var ex = assertThrows(BusinessException.class, () -> orderService.pay(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("已取消"));
        verify(orderMapper, times(2)).selectById(ORDER_NO);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("P8 乐观锁冲突, 重查为 null（极端）-> BusinessException, event 未发布")
    void payOptLockConflictThenNull() {
        when(orderMapper.selectById(ORDER_NO))
                .thenReturn(orderWithStatus(OrderStatus.UNPAID))
                .thenReturn(null);
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(0);

        var ex = assertThrows(BusinessException.class, () -> orderService.pay(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("不存在"));
        verify(orderMapper, times(2)).selectById(ORDER_NO);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("P9 两线程同时支付同订单 -> 一成功, 一获异常, 事件只发 1 次")
    void payConcurrentTwoThreads() throws Exception {
        var unpaid = orderWithStatus(OrderStatus.UNPAID);
        var paid   = orderWithStatus(OrderStatus.PAID);

        var selectN = new AtomicInteger(0);
        doAnswer(inv -> selectN.getAndIncrement() < 2 ? unpaid : paid)
                .when(orderMapper).selectById(ORDER_NO);

        var updateN = new AtomicInteger(0);
        doAnswer(inv -> updateN.getAndIncrement() == 0 ? 1 : 0)
                .when(orderMapper).update(any(SeckillOrder.class), any());

        var latch = new CountDownLatch(2);
        var results = Collections.synchronizedList(new ArrayList<String>());

        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try { orderService.pay(ORDER_NO, USER_A); results.add("success"); }
                catch (BusinessException e) { results.add(e.getMessage()); }
                finally { latch.countDown(); }
            }).start();
        }
        latch.await();

        assertEquals(2, results.size());
        assertEquals(1, results.stream().filter("success"::equals).count(), "恰好一个线程成功");
        verify(eventPublisher, times(1)).publishEvent(any(OrderPaidEvent.class));
    }

    @Test
    @DisplayName("P10 支付 + 取消同时竞争 -> 一成功, 事件只发 1 次")
    void payAndCancelConcurrent() throws Exception {
        var unpaid = orderWithStatus(OrderStatus.UNPAID);
        var paid   = orderWithStatus(OrderStatus.PAID);

        var selectN = new AtomicInteger(0);
        doAnswer(inv -> selectN.getAndIncrement() < 2 ? unpaid : paid)
                .when(orderMapper).selectById(ORDER_NO);

        var updateN = new AtomicInteger(0);
        doAnswer(inv -> updateN.getAndIncrement() == 0 ? 1 : 0)
                .when(orderMapper).update(any(SeckillOrder.class), any());

        var latch = new CountDownLatch(2);
        var results = Collections.synchronizedList(new ArrayList<String>());

        new Thread(() -> {
            try { orderService.pay(ORDER_NO, USER_A); results.add("pay_ok"); }
            catch (BusinessException e) { results.add("pay:" + e.getMessage()); }
            finally { latch.countDown(); }
        }).start();
        new Thread(() -> {
            try { orderService.cancel(ORDER_NO, USER_A); results.add("cancel_ok"); }
            catch (BusinessException e) { results.add("cancel:" + e.getMessage()); }
            finally { latch.countDown(); }
        }).start();
        latch.await();

        assertEquals(2, results.size());
        assertEquals(1, results.stream().filter(r -> r.endsWith("_ok")).count(), "恰好一个操作成功");
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    // ========================================================================
    // 十、取消 C1-C9
    // ========================================================================

    @Test
    @DisplayName("C1 orderNo null / userId null / 都 null -> IAE")
    void cancelNullArgs() {
        assertIAE(() -> orderService.cancel(null, USER_A), "orderNo");
        assertIAE(() -> orderService.cancel(ORDER_NO, null), "userId");
        assertIAE(() -> orderService.cancel(null, null), "orderNo");
    }

    @Test
    @DisplayName("C1.5 userId 与订单不匹配 -> BusinessException, update/event 未调用")
    void cancelUserIdMismatch() {
        var o = orderWithStatus(OrderStatus.UNPAID);
        o.setUserId(99999L); // 另一个用户
        when(orderMapper.selectById(ORDER_NO)).thenReturn(o);

        var ex = assertThrows(BusinessException.class, () -> orderService.cancel(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("无权取消"));
        verify(orderMapper, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("C2 UNPAID -> CANCELLED, cancelTime 设置, update 调用, 发布 OrderCancelledEvent")
    void cancelSuccess() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(orderWithStatus(OrderStatus.UNPAID));
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(1);

        orderService.cancel(ORDER_NO, USER_A);

        var captor = ArgumentCaptor.forClass(SeckillOrder.class);
        verify(orderMapper).update(captor.capture(), any());
        assertEquals(OrderStatus.CANCELLED, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getCancelTime());
        var eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        var evt = eventCaptor.getValue();
        assertEquals(ORDER_NO, evt.orderNo());
        assertEquals(ORDER_TOKEN, evt.orderToken());
        assertEquals(ACTIVITY_ID, evt.activityId());
        assertEquals(GOODS_ID, evt.seckillGoodsId());
        assertEquals(USER_A, evt.userId());
        assertEquals(2, evt.buyCount());
    }

    @Test
    @DisplayName("C3 order 不存在 -> BusinessException, update/event 未调用")
    void cancelOrderNotFound() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(null);

        var ex = assertThrows(BusinessException.class, () -> orderService.cancel(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("不存在"));
        verify(orderMapper, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("C4 已 PAID -> BusinessException, update/event 未调用")
    void cancelAlreadyPaid() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(orderWithStatus(OrderStatus.PAID));

        var ex = assertThrows(BusinessException.class, () -> orderService.cancel(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("只能取消待支付订单"));
        verify(orderMapper, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("C5 已 CANCELLED -> BusinessException, update/event 未调用")
    void cancelAlreadyCancelled() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(orderWithStatus(OrderStatus.CANCELLED));

        var ex = assertThrows(BusinessException.class, () -> orderService.cancel(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("只能取消待支付订单"));
        verify(orderMapper, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("C6 乐观锁冲突, 重查为 PAID -> BusinessException, event 未发布")
    void cancelOptLockConflictThenPaid() {
        when(orderMapper.selectById(ORDER_NO))
                .thenReturn(orderWithStatus(OrderStatus.UNPAID))
                .thenReturn(orderWithStatus(OrderStatus.PAID));
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(0);

        var ex = assertThrows(BusinessException.class, () -> orderService.cancel(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("已支付"));
        verify(orderMapper, times(2)).selectById(ORDER_NO);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("C7 乐观锁冲突, 重查为 CANCELLED -> BusinessException, event 未发布")
    void cancelOptLockConflictThenCancelled() {
        when(orderMapper.selectById(ORDER_NO))
                .thenReturn(orderWithStatus(OrderStatus.UNPAID))
                .thenReturn(orderWithStatus(OrderStatus.CANCELLED));
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(0);

        var ex = assertThrows(BusinessException.class, () -> orderService.cancel(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("已取消"));
        verify(orderMapper, times(2)).selectById(ORDER_NO);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("C8 乐观锁冲突, 重查为 null（极端）-> BusinessException, event 未发布")
    void cancelOptLockConflictThenNull() {
        when(orderMapper.selectById(ORDER_NO))
                .thenReturn(orderWithStatus(OrderStatus.UNPAID))
                .thenReturn(null);
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(0);

        var ex = assertThrows(BusinessException.class, () -> orderService.cancel(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("不存在"));
        verify(orderMapper, times(2)).selectById(ORDER_NO);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("C9 两线程同时取消同订单 -> 一成功, 事件只发 1 次")
    void cancelConcurrentTwoThreads() throws Exception {
        var unpaid    = orderWithStatus(OrderStatus.UNPAID);
        var cancelled = orderWithStatus(OrderStatus.CANCELLED);

        var selectN = new AtomicInteger(0);
        doAnswer(inv -> selectN.getAndIncrement() < 2 ? unpaid : cancelled)
                .when(orderMapper).selectById(ORDER_NO);

        var updateN = new AtomicInteger(0);
        doAnswer(inv -> updateN.getAndIncrement() == 0 ? 1 : 0)
                .when(orderMapper).update(any(SeckillOrder.class), any());

        var latch = new CountDownLatch(2);
        var results = Collections.synchronizedList(new ArrayList<String>());

        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try { orderService.cancel(ORDER_NO, USER_A); results.add("success"); }
                catch (BusinessException e) { results.add(e.getMessage()); }
                finally { latch.countDown(); }
            }).start();
        }
        latch.await();

        assertEquals(2, results.size());
        assertEquals(1, results.stream().filter("success"::equals).count(), "恰好一个线程成功");
        verify(eventPublisher, times(1)).publishEvent(any(OrderCancelledEvent.class));
    }

    // ========================================================================
    // 十一、状态查询 S1-S6
    // ========================================================================

    @Test
    @DisplayName("S1 orderToken null -> IAE")
    void statusNullToken() {
        assertIAE(() -> orderService.getOrderStatus(null), "orderToken");
    }

    // ========================================================================
    // 十一-B、状态查询 VO（归属校验）
    // ========================================================================

    @Test
    @DisplayName("VO1 orderToken null -> IAE")
    void statusVONullToken() {
        assertIAE(() -> orderService.getOrderStatusVO(null, USER_A), "orderToken");
    }

    @Test
    @DisplayName("VO2 不存在 -> null")
    void statusVONotFound() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        assertNull(orderService.getOrderStatusVO(ORDER_TOKEN, USER_A));
    }

    @Test
    @DisplayName("VO3 属于当前用户 -> OrderStatusVO")
    void statusVOOwned() {
        when(orderMapper.selectOne(any())).thenReturn(orderWithStatus(OrderStatus.UNPAID));
        var vo = orderService.getOrderStatusVO(ORDER_TOKEN, USER_A);
        assertNotNull(vo);
        assertEquals("UNPAID", vo.status());
        assertEquals(ORDER_NO, vo.orderNo());
    }

    @Test
    @DisplayName("VO4 不属于当前用户 -> null")
    void statusVONotOwned() {
        when(orderMapper.selectOne(any())).thenReturn(orderWithStatus(OrderStatus.UNPAID));
        assertNull(orderService.getOrderStatusVO(ORDER_TOKEN, 99999L));
    }

    @Test
    @DisplayName("S2 orderToken blank -> IAE")
    void statusBlankToken() {
        assertIAE(() -> orderService.getOrderStatus(""), "orderToken");
        assertIAE(() -> orderService.getOrderStatus("  "), "orderToken");
    }

    @Test
    @DisplayName("S3 不存在 -> null")
    void statusNotFound() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        assertNull(orderService.getOrderStatus(ORDER_TOKEN));
    }

    @Test
    @DisplayName("S4 UNPAID -> \"UNPAID\"")
    void statusUnpaid() {
        when(orderMapper.selectOne(any())).thenReturn(orderWithStatus(OrderStatus.UNPAID));
        assertEquals("UNPAID", orderService.getOrderStatus(ORDER_TOKEN));
    }

    @Test
    @DisplayName("S5 PAID -> \"PAID\"")
    void statusPaid() {
        when(orderMapper.selectOne(any())).thenReturn(orderWithStatus(OrderStatus.PAID));
        assertEquals("PAID", orderService.getOrderStatus(ORDER_TOKEN));
    }

    @Test
    @DisplayName("S6 CANCELLED -> \"CANCELLED\"")
    void statusCancelled() {
        when(orderMapper.selectOne(any())).thenReturn(orderWithStatus(OrderStatus.CANCELLED));
        assertEquals("CANCELLED", orderService.getOrderStatus(ORDER_TOKEN));
    }

    // ========================================================================
    // 十二、超时取消 TO1-TO11
    // ========================================================================

    @Test
    @DisplayName("TO1 orderToken null -> IAE")
    void cancelByTimeoutNullToken() {
        assertIAE(() -> orderService.cancelByTimeout(null), "orderToken");
    }

    @Test
    @DisplayName("TO2 orderToken blank -> IAE")
    void cancelByTimeoutBlankToken() {
        assertIAE(() -> orderService.cancelByTimeout(""), "orderToken");
        assertIAE(() -> orderService.cancelByTimeout("  "), "orderToken");
    }

    @Test
    @DisplayName("TO3 UNPAID -> CANCELLED, 乐观锁命中, OrderTimedOutEvent 含全字段")
    void cancelByTimeoutSuccess() {
        when(orderMapper.selectOne(any())).thenReturn(orderWithStatus(OrderStatus.UNPAID));
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(1);

        orderService.cancelByTimeout(ORDER_TOKEN);

        var captor = ArgumentCaptor.forClass(SeckillOrder.class);
        verify(orderMapper).update(captor.capture(), any());
        assertEquals(OrderStatus.CANCELLED, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getCancelTime());

        var evtCaptor = ArgumentCaptor.forClass(OrderTimedOutEvent.class);
        verify(eventPublisher).publishEvent(evtCaptor.capture());
        var evt = evtCaptor.getValue();
        assertEquals(ORDER_TOKEN, evt.orderToken());
        assertEquals(ORDER_NO, evt.orderNo());
        assertEquals(ACTIVITY_ID, evt.activityId());
        assertEquals(GOODS_ID, evt.seckillGoodsId());
        assertEquals(USER_A, evt.userId());
        assertEquals(2, evt.buyCount());
    }

    @Test
    @DisplayName("TO4 订单不存在 -> BusinessException")
    void cancelByTimeoutNotFound() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        var ex = assertThrows(BusinessException.class, () -> orderService.cancelByTimeout(ORDER_TOKEN));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("TO5 已 PAID -> BusinessException")
    void cancelByTimeoutAlreadyPaid() {
        when(orderMapper.selectOne(any())).thenReturn(orderWithStatus(OrderStatus.PAID));
        var ex = assertThrows(BusinessException.class, () -> orderService.cancelByTimeout(ORDER_TOKEN));
        assertTrue(ex.getMessage().contains("只能取消待支付订单"));
    }

    @Test
    @DisplayName("TO6 已 CANCELLED -> BusinessException")
    void cancelByTimeoutAlreadyCancelled() {
        when(orderMapper.selectOne(any())).thenReturn(orderWithStatus(OrderStatus.CANCELLED));
        var ex = assertThrows(BusinessException.class, () -> orderService.cancelByTimeout(ORDER_TOKEN));
        assertTrue(ex.getMessage().contains("只能取消待支付订单"));
    }

    @Test
    @DisplayName("TO7 乐观锁冲突 -> 重查为 PAID -> BusinessException(已支付)")
    void cancelByTimeoutConflictThenPaid() {
        when(orderMapper.selectOne(any()))
                .thenReturn(orderWithStatus(OrderStatus.UNPAID))
                .thenReturn(orderWithStatus(OrderStatus.PAID));
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(0);

        var ex = assertThrows(BusinessException.class, () -> orderService.cancelByTimeout(ORDER_TOKEN));
        assertTrue(ex.getMessage().contains("已支付"));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("TO8 乐观锁冲突 -> 重查为 CANCELLED -> BusinessException(已取消)")
    void cancelByTimeoutConflictThenCancelled() {
        when(orderMapper.selectOne(any()))
                .thenReturn(orderWithStatus(OrderStatus.UNPAID))
                .thenReturn(orderWithStatus(OrderStatus.CANCELLED));
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(0);

        var ex = assertThrows(BusinessException.class, () -> orderService.cancelByTimeout(ORDER_TOKEN));
        assertTrue(ex.getMessage().contains("已取消"));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("TO9 乐观锁冲突 -> 重查为 null -> BusinessException(不存在)")
    void cancelByTimeoutConflictThenNull() {
        when(orderMapper.selectOne(any()))
                .thenReturn(orderWithStatus(OrderStatus.UNPAID))
                .thenReturn(null);
        when(orderMapper.update(any(SeckillOrder.class), any())).thenReturn(0);

        var ex = assertThrows(BusinessException.class, () -> orderService.cancelByTimeout(ORDER_TOKEN));
        assertTrue(ex.getMessage().contains("不存在"));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("TO10 两线程同时超时取消同订单 -> 恰好一个成功, 事件只发 1 次")
    void cancelByTimeoutConcurrentTwoThreads() throws Exception {
        var unpaid = orderWithStatus(OrderStatus.UNPAID);
        var cancelled = orderWithStatus(OrderStatus.CANCELLED);

        var selectN = new AtomicInteger(0);
        doAnswer(inv -> selectN.getAndIncrement() < 2 ? unpaid : cancelled)
                .when(orderMapper).selectOne(any());

        var updateN = new AtomicInteger(0);
        doAnswer(inv -> updateN.getAndIncrement() == 0 ? 1 : 0)
                .when(orderMapper).update(any(SeckillOrder.class), any());

        var latch = new CountDownLatch(2);
        var results = Collections.synchronizedList(new ArrayList<String>());

        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try { orderService.cancelByTimeout(ORDER_TOKEN); results.add("success"); }
                catch (BusinessException e) { results.add(e.getMessage()); }
                finally { latch.countDown(); }
            }).start();
        }
        latch.await();

        assertEquals(2, results.size());
        assertEquals(1, results.stream().filter("success"::equals).count());
        verify(eventPublisher, times(1)).publishEvent(any(OrderTimedOutEvent.class));
    }

    @Test
    @DisplayName("TO11 支付 vs 超时取消并发 -> 最多一个成功")
    void cancelByTimeoutAndPayConcurrent() throws Exception {
        var unpaid = orderWithStatus(OrderStatus.UNPAID);
        // 每个 select 调用返回新对象避免实体状态被共享修改
        doAnswer(inv -> orderWithStatus(OrderStatus.UNPAID))
                .when(orderMapper).selectOne(any());
        doAnswer(inv -> orderWithStatus(OrderStatus.UNPAID))
                .when(orderMapper).selectById(ORDER_NO);

        var updateN = new AtomicInteger(0);
        doAnswer(inv -> updateN.getAndIncrement() == 0 ? 1 : 0)
                .when(orderMapper).update(any(SeckillOrder.class), any());

        var latch = new CountDownLatch(2);
        var results = Collections.synchronizedList(new ArrayList<String>());

        new Thread(() -> {
            try { orderService.cancelByTimeout(ORDER_TOKEN); results.add("timeout_ok"); }
            catch (BusinessException e) { results.add("timeout:" + e.getMessage()); }
            finally { latch.countDown(); }
        }).start();
        new Thread(() -> {
            try { orderService.pay(ORDER_NO, USER_A); results.add("pay_ok"); }
            catch (BusinessException e) { results.add("pay:" + e.getMessage()); }
            finally { latch.countDown(); }
        }).start();
        latch.await();

        assertEquals(2, results.size());
        long successCount = results.stream().filter(r -> r.endsWith("_ok")).count();
        assertEquals(1, successCount, "恰好一个操作成功");
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }
}
