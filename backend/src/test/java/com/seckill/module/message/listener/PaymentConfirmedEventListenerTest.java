package com.seckill.module.message.listener;

import com.seckill.module.activity.mapper.ActivityMapper;
import com.seckill.module.activity.model.entity.Activity;
import com.seckill.module.message.model.enums.MessageType;
import com.seckill.module.message.service.UserMessageService;
import com.seckill.module.order.mapper.SeckillOrderMapper;
import com.seckill.module.order.model.entity.SeckillOrder;
import com.seckill.module.payment.model.dto.PaymentConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link PaymentConfirmedEventListener} 单元测试。
 *
 * <p>验证支付确认后两条通知的正确发送、部分降级和异常隔离。</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentConfirmedEventListenerTest {

    private static final Long ORDER_NO = 9001L;
    private static final Long USER_ID = 10001L;
    private static final Long MERCHANT_ID = 20001L;
    private static final Long ACTIVITY_ID = 100L;
    private static final BigDecimal AMOUNT = new BigDecimal("19.99");
    private static final LocalDateTime PAY_TIME = LocalDateTime.of(2026, 7, 30, 12, 0);

    @Mock
    private SeckillOrderMapper orderMapper;
    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private UserMessageService userMessageService;

    private PaymentConfirmedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentConfirmedEventListener(orderMapper, activityMapper, userMessageService);
    }

    // ============ helpers ============

    private SeckillOrder order() {
        var o = new SeckillOrder();
        o.setOrderNo(ORDER_NO);
        o.setUserId(USER_ID);
        o.setActivityId(ACTIVITY_ID);
        o.setTotalAmount(AMOUNT);
        return o;
    }

    private Activity activity() {
        var a = new Activity();
        a.setActivityId(ACTIVITY_ID);
        a.setMerchantId(MERCHANT_ID);
        return a;
    }

    private PaymentConfirmedEvent event() {
        return new PaymentConfirmedEvent(ORDER_NO, USER_ID, AMOUNT, PAY_TIME);
    }

    // ================================================================
    // Happy path
    // ================================================================

    @Test
    @DisplayName("P1 全链路正常 -> 插 2 条 user_message（商家 + 买家）")
    void handleNormal() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(order());
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity());

        listener.handlePaymentConfirmed(event());

        var captor = ArgumentCaptor.forClass(MessageType.class);
        verify(userMessageService, times(2)).sendMessage(
                anyLong(), captor.capture(), anyString(), anyLong());

        assertEquals(2, captor.getAllValues().size());
        assertTrue(captor.getAllValues().contains(MessageType.payment_notify_merchant));
        assertTrue(captor.getAllValues().contains(MessageType.payment_notify_user));
    }

    @Test
    @DisplayName("P1a 商家通知含金额参数")
    void merchantNotifyContent() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(order());
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity());

        listener.handlePaymentConfirmed(event());

        var captor = ArgumentCaptor.forClass(String.class);
        verify(userMessageService, times(2)).sendMessage(
                anyLong(), any(MessageType.class), captor.capture(), anyLong());

        String merchantContent = captor.getAllValues().get(0);
        assertTrue(merchantContent.contains(ORDER_NO.toString()));
        assertTrue(merchantContent.contains(AMOUNT.toString()));
    }

    @Test
    @DisplayName("P1b 买家通知含金额参数")
    void userNotifyContent() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(order());
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity());

        listener.handlePaymentConfirmed(event());

        var captor = ArgumentCaptor.forClass(String.class);
        verify(userMessageService, times(2)).sendMessage(
                anyLong(), any(MessageType.class), captor.capture(), anyLong());

        String userContent = captor.getAllValues().get(1);
        assertTrue(userContent.contains(ORDER_NO.toString()));
        assertTrue(userContent.contains(AMOUNT.toString()));
    }

    // ================================================================
    // 降级
    // ================================================================

    @Test
    @DisplayName("D1 订单不存在 -> log.warn, 不插任何消息")
    void orderNotFound() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(null);

        listener.handlePaymentConfirmed(event());

        verifyNoInteractions(activityMapper, userMessageService);
    }

    @Test
    @DisplayName("D2 活动不存在 -> 仅买家通知, log.warn 活动不存在")
    void activityNotFound() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(order());
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

        listener.handlePaymentConfirmed(event());

        verify(userMessageService).sendMessage(
                eq(USER_ID), eq(MessageType.payment_notify_user), anyString(), eq(ACTIVITY_ID));
        verify(userMessageService, never()).sendMessage(
                eq(MERCHANT_ID), any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("D3 商家 insert 失败 -> log.error, 买家 insert 仍执行")
    void merchantInsertFailed() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(order());
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity());
        doThrow(new RuntimeException("DB error"))
                .when(userMessageService).sendMessage(
                        eq(MERCHANT_ID), eq(MessageType.payment_notify_merchant), anyString(), anyLong());

        listener.handlePaymentConfirmed(event());

        verify(userMessageService).sendMessage(
                eq(USER_ID), eq(MessageType.payment_notify_user), anyString(), eq(ACTIVITY_ID));
    }

    @Test
    @DisplayName("D4 买家 insert 失败 -> log.error, 商家 insert 不回滚（best-effort）")
    void userInsertFailed() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(order());
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity());
        doThrow(new RuntimeException("DB error"))
                .when(userMessageService).sendMessage(
                        eq(USER_ID), eq(MessageType.payment_notify_user), anyString(), anyLong());

        listener.handlePaymentConfirmed(event());

        verify(userMessageService).sendMessage(
                eq(MERCHANT_ID), eq(MessageType.payment_notify_merchant), anyString(), eq(ACTIVITY_ID));
    }

    @Test
    @DisplayName("D5 商家和买家 insert 都失败 -> 两个异常各自 catch, 不向上传播")
    void bothInsertFailed() {
        when(orderMapper.selectById(ORDER_NO)).thenReturn(order());
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity());
        doThrow(new RuntimeException("DB error merchant"))
                .when(userMessageService).sendMessage(
                        eq(MERCHANT_ID), eq(MessageType.payment_notify_merchant), anyString(), anyLong());
        doThrow(new RuntimeException("DB error user"))
                .when(userMessageService).sendMessage(
                        eq(USER_ID), eq(MessageType.payment_notify_user), anyString(), anyLong());

        // 不抛异常
        listener.handlePaymentConfirmed(event());

        verify(userMessageService, times(2)).sendMessage(
                anyLong(), any(MessageType.class), anyString(), anyLong());
    }

    // ================================================================
    // 边界
    // ================================================================

    @Test
    @DisplayName("E1 event null -> log.warn, 不调 Mapper/Service")
    void nullEvent() {
        listener.handlePaymentConfirmed(null);

        verifyNoInteractions(orderMapper, activityMapper, userMessageService);
    }

    @Test
    @DisplayName("E2 event.orderNo null -> log.warn, 跳过查询")
    void nullOrderNo() {
        var event = new PaymentConfirmedEvent(null, USER_ID, AMOUNT, LocalDateTime.now());

        listener.handlePaymentConfirmed(event);

        verifyNoInteractions(orderMapper, activityMapper, userMessageService);
    }

    @Test
    @DisplayName("E3 金额为零 -> 模板正确填充")
    void zeroAmount() {
        var zeroEvent = new PaymentConfirmedEvent(ORDER_NO, USER_ID, BigDecimal.ZERO, PAY_TIME);
        when(orderMapper.selectById(ORDER_NO)).thenReturn(order());
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity());

        listener.handlePaymentConfirmed(zeroEvent);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(userMessageService, times(2)).sendMessage(
                anyLong(), any(MessageType.class), captor.capture(), anyLong());

        assertTrue(captor.getAllValues().get(0).contains("0"));
    }
}
