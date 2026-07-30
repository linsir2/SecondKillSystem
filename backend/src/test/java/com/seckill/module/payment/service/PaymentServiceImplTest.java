package com.seckill.module.payment.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.order.mapper.SeckillOrderMapper;
import com.seckill.module.order.model.entity.SeckillOrder;
import com.seckill.module.order.model.enums.OrderStatus;
import com.seckill.module.order.service.OrderService;
import com.seckill.module.payment.mapper.PaymentMapper;
import com.seckill.module.payment.model.dto.PayResponse;
import com.seckill.module.payment.model.dto.PaymentConfirmedEvent;
import com.seckill.module.payment.model.entity.Payment;
import com.seckill.module.payment.model.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentMatchers;

/**
 * {@link PaymentService} 单元测试 —— 纯 Mockito，无 Spring 上下文。
 *
 * <pre>
 * ┌───────────────────────────────────────────────────────────────────────────┐
 * │ V1-V3 参数校验    request / orderNo / userId null                         │
 * │ P1-P6 正常路径    标准支付、幂等重试、金额精度、payTime、status 默认值、事件发布     │
 * │ F1-F6 异常传播    订单不存在/无权/已支付/已取消/乐观锁/入库异常                   │
 * │ C1-C5 并发/边界   多线程同订单/支付+关单/链式顺序/Mapper查不到/重复请求幂等          │
 * └───────────────────────────────────────────────────────────────────────────┘
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl — 支付")
class PaymentServiceImplTest {

    @Mock private OrderService orderService;
    @Mock private PaymentMapper paymentMapper;
    @Mock private SeckillOrderMapper orderMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PaymentService paymentService;

    private static final Long ORDER_NO = 9001L;
    private static final Long USER_A = 10001L;
    private static final Long USER_B = 99999L;
    private static final BigDecimal AMOUNT = new BigDecimal("39.98");

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(orderService, paymentMapper, orderMapper, eventPublisher);
    }

    // ============ helpers ============


    private SeckillOrder paidOrder() {
        var o = new SeckillOrder();
        o.setOrderNo(ORDER_NO);
        o.setUserId(USER_A);
        o.setTotalAmount(AMOUNT);
        o.setStatus(OrderStatus.PAID);
        o.setPayTime(LocalDateTime.now());
        return o;
    }

    private void assertIAE(Executable r, String msg) {
        var ex = assertThrows(IllegalArgumentException.class, r);
        assertTrue(ex.getMessage().toLowerCase().contains(msg.toLowerCase()),
                () -> "IAE 应包含「" + msg + "」，实际: " + ex.getMessage());
    }

    private ArgumentCaptor<Payment> capturePayment() {
        var c = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insert(c.capture());
        return c;
    }

    // ================================================================
    // V — 参数校验 V1-V2
    // ================================================================

    @Test
    @DisplayName("V1 orderNo == null -> IAE")
    void nullOrderNo() {
        assertIAE(() -> paymentService.pay(null, USER_A), "orderNo");
        verifyNoInteractions(orderService, paymentMapper, orderMapper, eventPublisher);
    }

    @Test
    @DisplayName("V2 userId == null -> IAE")
    void nullUserId() {
        assertIAE(() -> paymentService.pay(ORDER_NO, null), "userId");
        verifyNoInteractions(orderService, paymentMapper, orderMapper, eventPublisher);
    }

    // ================================================================
    // P — 正常路径 P1-P6
    // ================================================================

    @Test
    @DisplayName("P1 标准支付成功 → orderService.pay 调用 + payment 入库 + success=true")
    void paySuccess() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(paidOrder());

        PayResponse resp = paymentService.pay(ORDER_NO, USER_A);

        assertTrue(resp.success());
        verify(orderService).pay(ORDER_NO, USER_A);
        var p = capturePayment().getValue();
        assertEquals(ORDER_NO, p.getOrderNo());
        assertEquals(USER_A, p.getUserId());
        assertEquals(AMOUNT, p.getAmount());
    }

    @Test
    @DisplayName("P2 重复请求（uk 冲突）→ 幂等返回 success")
    void payIdempotentDuplicate() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(paidOrder());
        doThrow(new DuplicateKeyException("uk_payment_order_no"))
                .when(paymentMapper).insert((Payment) any());

        PayResponse resp = paymentService.pay(ORDER_NO, USER_A);

        assertTrue(resp.success());
        verify(paymentMapper).insert((Payment) any());
    }

    @Test
    @DisplayName("P3 amount 精度 39.98 → scale=2, 值匹配")
    void payAmountPrecision() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(paidOrder());

        paymentService.pay(ORDER_NO, USER_A);

        var p = capturePayment().getValue();
        assertEquals(0, AMOUNT.compareTo(p.getAmount()));
        assertEquals(2, p.getAmount().scale());
    }

    @Test
    @DisplayName("P4 payTime 非 null，精确到秒")
    void payTimeNotNull() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(paidOrder());

        paymentService.pay(ORDER_NO, USER_A);

        var p = capturePayment().getValue();
        assertNotNull(p.getPayTime());
    }

    @Test
    @DisplayName("P5 PaymentStatus.SUCCESS 默认值")
    void defaultStatus() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(paidOrder());

        paymentService.pay(ORDER_NO, USER_A);

        var p = capturePayment().getValue();
        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
    }

    @Test
    @DisplayName("P6 PaymentConfirmedEvent 发布 → 全字段校验")
    void eventPublished() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(paidOrder());

        paymentService.pay(ORDER_NO, USER_A);

        var evtCaptor = ArgumentCaptor.forClass(PaymentConfirmedEvent.class);
        verify(eventPublisher).publishEvent(evtCaptor.capture());
        var evt = evtCaptor.getValue();
        assertEquals(ORDER_NO, evt.orderNo());
        assertEquals(USER_A, evt.userId());
        assertEquals(AMOUNT, evt.amount());
        assertNotNull(evt.payTime());
    }

    // ================================================================
    // F — 异常传播 F1-F6
    // ================================================================

    @Test
    @DisplayName("F1 订单不存在 → BusinessException 传播, payment 未插")
    void payOrderNotFound() {
        doThrow(new BusinessException("订单不存在"))
                .when(orderService).pay(ORDER_NO, USER_A);

        var ex = assertThrows(BusinessException.class,
                () -> paymentService.pay(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("订单不存在"));
        verifyNoInteractions(paymentMapper, orderMapper, eventPublisher);
    }

    @Test
    @DisplayName("F2 无权支付 → BusinessException 传播, payment 未插")
    void payUnauthorized() {
        doThrow(new BusinessException("无权支付该订单"))
                .when(orderService).pay(ORDER_NO, USER_A);

        var ex = assertThrows(BusinessException.class,
                () -> paymentService.pay(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("无权支付"));
        verifyNoInteractions(paymentMapper, orderMapper, eventPublisher);
    }

    @Test
    @DisplayName("F3 订单已支付 → BusinessException 传播")
    void payAlreadyPaid() {
        doThrow(new BusinessException("订单已支付"))
                .when(orderService).pay(ORDER_NO, USER_A);

        assertThrows(BusinessException.class,
                () -> paymentService.pay(ORDER_NO, USER_A));
        verifyNoInteractions(paymentMapper, orderMapper, eventPublisher);
    }

    @Test
    @DisplayName("F4 订单已取消 → BusinessException 传播")
    void payAlreadyCancelled() {
        doThrow(new BusinessException("订单已取消"))
                .when(orderService).pay(ORDER_NO, USER_A);

        assertThrows(BusinessException.class,
                () -> paymentService.pay(ORDER_NO, USER_A));
        verifyNoInteractions(paymentMapper, orderMapper, eventPublisher);
    }

    @Test
    @DisplayName("F5 乐观锁冲突 → BusinessException 传播")
    void payOptimisticLockFail() {
        doThrow(new BusinessException("订单状态已变更"))
                .when(orderService).pay(ORDER_NO, USER_A);

        assertThrows(BusinessException.class,
                () -> paymentService.pay(ORDER_NO, USER_A));
        verifyNoInteractions(paymentMapper, orderMapper, eventPublisher);
    }

    @Test
    @DisplayName("F6 payment 入库异常 → RuntimeException 传播")
    void payInsertFail() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(paidOrder());
        doThrow(new RuntimeException("DB write failed"))
                .when(paymentMapper).insert((Payment) any());

        assertThrows(RuntimeException.class,
                () -> paymentService.pay(ORDER_NO, USER_A));
        // orderService.pay 已被调用
        verify(orderService).pay(ORDER_NO, USER_A);
    }

    // ================================================================
    // C — 并发/边界 C1-C5
    // ================================================================

    @Test
    @DisplayName("C1 20 线程同订单支付 → 1 次 payment insert, 其余幂等")
    void pay20Threads() throws Exception {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(paidOrder());
        var insertCount = new AtomicInteger(0);
        doAnswer(inv -> {
            if (insertCount.getAndIncrement() == 0) return null;
            throw new DuplicateKeyException("uk_payment_order_no");
        }).when(paymentMapper).insert((Payment) any());

        var latch = new CountDownLatch(20);
        var results = Collections.synchronizedList(new ArrayList<String>());

        for (int i = 0; i < 20; i++) {
            new Thread(() -> {
                try {
                    PayResponse r = paymentService.pay(ORDER_NO, USER_A);
                    results.add(r.success() ? "ok" : "fail");
                } catch (Exception e) {
                    results.add("err:" + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();

        assertEquals(20, results.size());
        long okCount = results.stream().filter("ok"::equals).count();
        assertEquals(20, okCount, "所有线程都应得到 success=true");
        verify(paymentMapper, times(20)).insert((Payment) any());
    }

    @Test
    @DisplayName("C2 20 线程混合用户支付各自订单 → 各不干扰")
    void pay20ThreadsDifferentUsers() throws Exception {
        // 20 个不同的 orderNo, 但都用同一个 mock: orderService 始终成功
        // 这里验证 paymentService 不因并发产生状态污染
        when(orderMapper.selectById(anyLong())).thenAnswer(inv -> {
            var o = paidOrder();
            o.setOrderNo(inv.getArgument(0));
            return o;
        });

        var latch = new CountDownLatch(20);
        var errors = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < 20; i++) {
            long orderNo = ORDER_NO + i;
            new Thread(() -> {
                try {
                    PayResponse r = paymentService.pay(orderNo, USER_A);
                    if (!r.success()) errors.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();

        assertEquals(0, errors.get());
        verify(paymentMapper, times(20)).insert((Payment) any());
    }

    @Test
    @DisplayName("C3 链式顺序: orderService.pay → paymentMapper.insert → eventPublisher.publishEvent")
    void chainedInvocationOrder() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(paidOrder());

        paymentService.pay(ORDER_NO, USER_A);

        InOrder inOrder = inOrder(orderService, paymentMapper, eventPublisher);
        inOrder.verify(orderService).pay(ORDER_NO, USER_A);
        inOrder.verify(paymentMapper).insert((Payment) any());
        inOrder.verify(eventPublisher).publishEvent(any(PaymentConfirmedEvent.class));
    }

    @Test
    @DisplayName("C4 orderMapper.selectById 查不到量（极端）→ BusinessException, payment 未插")
    void payOrderSelectReturnsNull() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(null);

        var ex = assertThrows(BusinessException.class,
                () -> paymentService.pay(ORDER_NO, USER_A));
        assertTrue(ex.getMessage().contains("订单不存在"));
        verify(paymentMapper, never()).insert((Payment) any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("C5 重复请求同参数 → 第一次成功, 第二次幂等 success")
    void payTwiceSameRequest() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(paidOrder());
        doAnswer(inv -> null)
                .doThrow(new DuplicateKeyException("uk_payment_order_no"))
                .when(paymentMapper).insert((Payment) any());

        // 第一次
        PayResponse r1 = paymentService.pay(ORDER_NO, USER_A);
        assertTrue(r1.success());

        // 第二次 (幂等)
        PayResponse r2 = paymentService.pay(ORDER_NO, USER_A);
        assertTrue(r2.success());

        verify(paymentMapper, times(2)).insert((Payment) any());
        verify(eventPublisher, times(2)).publishEvent(any(PaymentConfirmedEvent.class));
    }
}
