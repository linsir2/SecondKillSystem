package com.seckill.module.message.scheduler;

import com.seckill.module.order.mapper.MessageLogMapper;
import com.seckill.module.order.model.dto.OrderTimeoutMessage;
import com.seckill.module.order.model.entity.MessageLog;
import com.seckill.module.order.model.enums.SendStatus;
import com.seckill.module.order.service.OrderService;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link MessageLogScanner} 单元测试。
 *
 * <p>纯 Mockito，覆盖正常/异常/边界/并发安全语义。</p>
 *
 * <p>注意：MyBatis-Plus 3.5.9+ 的 BaseMapper 新增了 {@code updateById(Collection<T>)} 批量方法，
 * RocketMQTemplate 也有 {@code syncSend(String, Message<?>, long)} 和
 * {@code syncSend(String, Collection<T>, long)} 等重载，
 * 因此 {@code verify(mapper).updateById(any())} 编译歧义必须使用 {@code any(MessageLog.class)} 显式指定类型。</p>
 */
@ExtendWith(MockitoExtension.class)
class MessageLogScannerTest {

    @Mock
    private MessageLogMapper messageLogMapper;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private OrderService orderService;

    private MessageLogScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new MessageLogScanner(messageLogMapper, rocketMQTemplate, orderService);
    }

    // ==================== helpers ====================

    private MessageLog log(int id, SendStatus status, int retryCount) {
        var l = new MessageLog();
        l.setMsgId((long) id);
        l.setBizType("order_timeout");
        l.setBizId("token-" + id);
        l.setTopic("seckill_order");
        l.setTag("order_timeout");
        l.setBody("{\"orderToken\":\"token-" + id + "\",\"orderNo\":" + (9000 + id) + "}");
        l.setStatus(status);
        l.setRetryCount(retryCount);
        l.setCreatedAt(LocalDateTime.now().minusSeconds(30));
        return l;
    }

    /** 从 verify 捕获 syncSend 的 destination。 */
    private ArgumentCaptor<String> captureDest() {
        var c = ArgumentCaptor.forClass(String.class);
        verify(rocketMQTemplate).syncSend(c.capture(), any(), anyLong(), anyInt());
        return c;
    }

    /** 捕获所有 updateById 调用的参数。 */
    private ArgumentCaptor<MessageLog> captureUpdates(int times) {
        var c = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogMapper, times(times)).updateById(c.capture());
        return c;
    }

    // ================================================================
    // N — 正常路径
    // ================================================================

    @Test
    @DisplayName("N1 空表 -> 不调 MQ 不调 update")
    void emptyTable() {
        when(messageLogMapper.selectList(any())).thenReturn(List.of());

        scanner.scanAndSend();

        verify(rocketMQTemplate, never()).syncSend(anyString(), any(), anyLong(), anyInt());
        verify(messageLogMapper, never()).updateById(any(MessageLog.class));
    }

    @Test
    @DisplayName("N2 1 条 INIT 发送成功 -> SENT + sendTime")
    void singleSuccess() {
        var msgLog = log(1, SendStatus.INIT, 0);
        when(messageLogMapper.selectList(any())).thenReturn(List.of(msgLog));

        var sendResult = mock(SendResult.class);
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        scanner.scanAndSend();

        var dest = captureDest().getValue();
        assertEquals("seckill_order:order_timeout", dest);

        var updates = captureUpdates(1).getAllValues();
        assertEquals(SendStatus.SENT, updates.get(0).getStatus());
        assertNotNull(updates.get(0).getSendTime());
    }

    @Test
    @DisplayName("N3 3 条全部成功 -> 3 次 syncSend, 3 次 SENT UPDATE")
    void batchAllSuccess() {
        var logs = List.of(log(1, SendStatus.INIT, 0), log(2, SendStatus.INIT, 0), log(3, SendStatus.INIT, 0));
        when(messageLogMapper.selectList(any())).thenReturn(logs);

        var sendResult = mock(SendResult.class);
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        scanner.scanAndSend();

        verify(rocketMQTemplate, times(3)).syncSend(anyString(), any(), anyLong(), anyInt());
        var updates = captureUpdates(3).getAllValues();
        updates.forEach(u -> assertEquals(SendStatus.SENT, u.getStatus()));
    }

    @Test
    @DisplayName("N4 批量中个别失败 -> 成功条 SENT, 失败条 retry++")
    void batchPartialFailure() {
        var log1 = log(1, SendStatus.INIT, 0);
        var log2 = log(2, SendStatus.INIT, 0);
        var log3 = log(3, SendStatus.INIT, 0);
        when(messageLogMapper.selectList(any())).thenReturn(List.of(log1, log2, log3));

        var sendResult = mock(SendResult.class);
        when(rocketMQTemplate.syncSend(eq("seckill_order:order_timeout"),
                any(), anyLong(), anyInt()))
                .thenReturn(sendResult)
                .thenThrow(new RuntimeException("Broker not available"))
                .thenReturn(sendResult);

        scanner.scanAndSend();

        verify(rocketMQTemplate, times(3)).syncSend(anyString(), any(), anyLong(), anyInt());
        var updates = captureUpdates(3).getAllValues();

        // log1 → SENT
        assertEquals(SendStatus.SENT, updates.get(0).getStatus());
        // log2 → retry_count=1 (status=null means not touched in success path)
        assertEquals(1, (int) updates.get(1).getRetryCount());
        assertNull(updates.get(1).getStatus());
        // log3 → SENT
        assertEquals(SendStatus.SENT, updates.get(2).getStatus());
    }

    // ================================================================
    // F — 失败/异常
    // ================================================================

    @Test
    @DisplayName("F1 MQ broker 不可达 -> retry_count 0→1, status 不变")
    void mqBrokerDown() {
        var msgLog = log(1, SendStatus.INIT, 0);
        when(messageLogMapper.selectList(any())).thenReturn(List.of(msgLog));
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("Remoting connect fail"));

        scanner.scanAndSend();

        var updates = captureUpdates(1).getAllValues();
        assertEquals(1, (int) updates.get(0).getRetryCount());
        assertNull(updates.get(0).getStatus());
    }

    @Test
    @DisplayName("F2 retry 2→3 失败 -> status=FAIL, retry_count=3")
    void retryExhausted() {
        var msgLog = log(1, SendStatus.INIT, 2);
        when(messageLogMapper.selectList(any())).thenReturn(List.of(msgLog));
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("Broker down"));

        scanner.scanAndSend();

        var updates = captureUpdates(1).getAllValues();
        assertEquals(3, (int) updates.get(0).getRetryCount());
        assertEquals(SendStatus.FAIL, updates.get(0).getStatus());
    }

    @Test
    @DisplayName("F3 MQ 发送成功但 UPDATE 抛异常 -> 下轮重扫（重复投递由 consumer 幂等兜底）")
    void updateFailsAfterMqSuccess() {
        var msgLog = log(1, SendStatus.INIT, 0);
        when(messageLogMapper.selectList(any())).thenReturn(List.of(msgLog));
        var sendResult = mock(SendResult.class);
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);
        // 1st call (SENT update) 抛异常 → catch 块调 2nd (retry update) 不再抛
        doThrow(new RuntimeException("DB update fail"))
                .doReturn(1)
                .when(messageLogMapper).updateById(any(MessageLog.class));

        scanner.scanAndSend();

        verify(rocketMQTemplate).syncSend(anyString(), any(), anyLong(), anyInt());
        verify(messageLogMapper, times(2)).updateById(any(MessageLog.class));
    }

    @Test
    @DisplayName("F4 扫表时 DB 断连 -> 异常传播 @Scheduled 下轮重试")
    void dbConnectionLost() {
        when(messageLogMapper.selectList(any())).thenThrow(new RuntimeException("Connection refused"));

        scanner.scanAndSend();

        verify(rocketMQTemplate, never()).syncSend(anyString(), any(), anyLong(), anyInt());
        verify(messageLogMapper, never()).updateById(any(MessageLog.class));
    }

    @Test
    @DisplayName("F5 MQ 未配置（rocketMQTemplate=null）-> 走本地超时流程")
    void mqNotConfigured() {
        scanner = new MessageLogScanner(messageLogMapper, null, orderService);

        var old = log(1, SendStatus.INIT, 0);
        old.setCreatedAt(LocalDateTime.now().minusSeconds(120));
        when(messageLogMapper.selectList(any())).thenReturn(List.of(old));

        scanner.scanAndSend();

        verify(messageLogMapper).selectList(any());
        verify(orderService).cancelByTimeout("token-1");
    }

    @Test
    @DisplayName("F6 body 为 null -> 跳过该条（不下发 MQ），继续处理其余")
    void nullBody() {
        var bad = log(1, SendStatus.INIT, 0);
        bad.setBody(null);
        var good = log(2, SendStatus.INIT, 0);
        when(messageLogMapper.selectList(any())).thenReturn(List.of(bad, good));

        var sendResult = mock(SendResult.class);
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        scanner.scanAndSend();

        // 坏消息跳过，只有 good 下发
        verify(rocketMQTemplate, times(1)).syncSend(anyString(), any(), anyLong(), anyInt());
    }

    // ================================================================
    // F7 — 混合状态过滤
    // ================================================================

    @Test
    @DisplayName("F7 混合状态 -> 只处理 INIT + retry_count<3")
    void mixedStatusFilter() {
        // mock 模拟 DB 过滤后的结果：只返回 INIT + retry < 3 的记录
        var init0 = log(1, SendStatus.INIT, 0);
        var init2 = log(4, SendStatus.INIT, 2);
        when(messageLogMapper.selectList(any())).thenReturn(List.of(init0, init2));

        var sendResult = mock(SendResult.class);
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult, sendResult);

        scanner.scanAndSend();

        verify(rocketMQTemplate, times(2)).syncSend(anyString(), any(), anyLong(), anyInt());
    }

    // ================================================================
    // F8 — retry 2→3 时 MQ 成功但 DB UPDATE 抛异常
    // ================================================================

    @Test
    @DisplayName("F8 retry 边界: 最后一次尝试 MQ 成功但 UPDATE 抛异常 -> MQ 已发, 下轮重扫")
    void lastRetryMqSucceedsUpdateFails() {
        var msgLog = log(1, SendStatus.INIT, 2);
        when(messageLogMapper.selectList(any())).thenReturn(List.of(msgLog));
        var sendResult = mock(SendResult.class);
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);
        doThrow(new RuntimeException("DB error"))
                .when(messageLogMapper).updateById(any(MessageLog.class));

        scanner.scanAndSend();

        verify(rocketMQTemplate).syncSend(anyString(), any(), anyLong(), anyInt());
        verify(messageLogMapper, atLeastOnce()).updateById(any(MessageLog.class));
    }
}
