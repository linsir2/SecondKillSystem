package com.seckill.module.message.mq;

import com.seckill.common.constant.UserRole;
import com.seckill.module.activity.mapper.ActivityMapper;
import com.seckill.module.activity.mapper.SeckillGoodsMapper;
import com.seckill.module.user.model.entity.SysUser;
import com.seckill.module.activity.model.dto.ActivityApprovedEvent;
import com.seckill.module.activity.model.dto.ActivityRejectedEvent;
import com.seckill.module.activity.model.dto.ActivitySubmittedForReviewEvent;
import com.seckill.module.activity.model.entity.Activity;
import com.seckill.module.activity.model.entity.SeckillGoods;
import com.seckill.module.activity.model.enums.ActivityStatus;
import com.seckill.module.goods.model.dto.GoodsInfo;
import com.seckill.module.goods.service.GoodsService;
import com.seckill.module.message.model.enums.MessageType;
import com.seckill.module.message.service.UserMessageService;
import com.seckill.module.user.mapper.SysUserMapper;
import com.seckill.module.user.model.dto.UserBannedEvent;
import com.seckill.module.user.model.dto.UserInfo;
import com.seckill.module.user.model.dto.UserRegisteredEvent;
import com.seckill.module.user.model.dto.UserUnbannedEvent;
import com.seckill.module.message.websocket.NotificationWebSocketHandler;
import com.seckill.module.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageEventListenerTest {

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private SeckillGoodsMapper seckillGoodsMapper;
    @Mock
    private GoodsService goodsService;
    @Mock
    private UserMessageService userMessageService;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private UserService userService;
    @Mock
    private NotificationWebSocketHandler webSocketHandler;

    @InjectMocks
    private MessageEventListener listener;

    @Captor
    private ArgumentCaptor<String> contentCaptor;

    private final Long activityId = 10L;
    private final Long merchantId = 100L;

    private Activity anActivity() {
        Activity a = new Activity();
        a.setActivityId(activityId);
        a.setActivityName("618大促");
        a.setMerchantId(merchantId);
        a.setStatus(ActivityStatus.preheating);
        a.setStartTime(LocalDateTime.of(2026, 7, 28, 20, 0));
        a.setEndTime(LocalDateTime.of(2026, 7, 28, 23, 0));
        a.setCreatedAt(LocalDateTime.of(2026, 7, 25, 10, 0));
        return a;
    }

    private SeckillGoods aSeckillGoods(Long sgId, Long goodsId) {
        SeckillGoods sg = new SeckillGoods();
        sg.setSeckillGoodsId(sgId);
        sg.setActivityId(activityId);
        sg.setGoodsId(goodsId);
        sg.setSeckillPrice(new BigDecimal("0.01"));
        sg.setStock(100);
        sg.setLimitNum(1);
        return sg;
    }

    // ========================================================================
    // onActivityApproved
    // ========================================================================

    @Test
    void happyPath_sendsMerchantNotification() {
        Activity activity = anActivity();
        when(activityMapper.selectById(activityId)).thenReturn(activity);
        List<SeckillGoods> sgList = List.of(
                aSeckillGoods(1L, 1001L),
                aSeckillGoods(2L, 1002L));
        when(seckillGoodsMapper.selectList(any())).thenReturn(sgList);
        // v2 data prep: goods names
        when(goodsService.getGoodsInfoList(List.of(1001L, 1002L), merchantId))
                .thenReturn(List.of(
                        new GoodsInfo(1001L, "手机", BigDecimal.TEN, 200),
                        new GoodsInfo(1002L, "平板", BigDecimal.TEN, 100)));

        when(userService.getUserInfo(merchantId)).thenReturn(new UserInfo(merchantId, "商家A", "a@t.com", UserRole.merchant, null));

        listener.onActivityApproved(new ActivityApprovedEvent(activityId, merchantId));

        verify(userMessageService).sendMessage(eq(merchantId), eq(com.seckill.module.message.model.enums.MessageType.approval_result),
                anyString(), eq(activityId));
        verify(webSocketHandler).broadcast(anyString());
    }

    @Test
    void activityNotFound_skipsNotification() {
        when(activityMapper.selectById(activityId)).thenReturn(null);

        listener.onActivityApproved(new ActivityApprovedEvent(activityId, merchantId));

        verify(userMessageService, never()).sendMessage(anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void emptySeckillGoods_goodsCountIsZero() {
        when(activityMapper.selectById(activityId)).thenReturn(anActivity());
        when(seckillGoodsMapper.selectList(any())).thenReturn(List.of());

        listener.onActivityApproved(new ActivityApprovedEvent(activityId, merchantId));

        verify(userMessageService).sendMessage(eq(merchantId), eq(com.seckill.module.message.model.enums.MessageType.approval_result),
                contentCaptor.capture(), eq(activityId));
        assertThat(contentCaptor.getValue()).contains("共 0 件商品");
    }

    @Test
    void activityNameWithSpecialChars_passesRawToSendMessage() {
        Activity a = anActivity();
        a.setActivityName("<促销> & \"秒杀\"");
        when(activityMapper.selectById(activityId)).thenReturn(a);
        when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(aSeckillGoods(1L, 1001L)));
        when(userService.getUserInfo(merchantId)).thenReturn(new UserInfo(merchantId, "商家A", "a@t.com", UserRole.merchant, null));
        when(goodsService.getGoodsInfoList(List.of(1001L), merchantId))
                .thenReturn(List.of(new GoodsInfo(1001L, "手机", BigDecimal.TEN, 200)));

        listener.onActivityApproved(new ActivityApprovedEvent(activityId, merchantId));

        verify(userMessageService).sendMessage(eq(merchantId), eq(com.seckill.module.message.model.enums.MessageType.approval_result),
                contentCaptor.capture(), eq(activityId));
        // Listener 原样传递，转义在 Service 层做
        assertThat(contentCaptor.getValue()).contains("<促销> & \"秒杀\"");
    }

    @Test
    void sendMessageThrows_propagatesOut() {
        when(activityMapper.selectById(activityId)).thenReturn(anActivity());
        when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(aSeckillGoods(1L, 1001L)));
        doThrow(new RuntimeException("DB connection lost"))
                .when(userMessageService).sendMessage(anyLong(), any(), anyString(), anyLong());

        assertThatThrownBy(() ->
                listener.onActivityApproved(new ActivityApprovedEvent(activityId, merchantId)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB connection lost");
    }

    @Test
    void approved_specialCharsInMerchantName_broadcastIsValidJson() throws Exception {
        Activity activity = anActivity();
        activity.setActivityName("端午大促");
        when(activityMapper.selectById(activityId)).thenReturn(activity);
        when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(aSeckillGoods(1L, 1001L)));
        when(goodsService.getGoodsInfoList(List.of(1001L), merchantId))
                .thenReturn(List.of(new GoodsInfo(1001L, "手机", BigDecimal.TEN, 200)));
        UserInfo merchant = new UserInfo(merchantId, "小米\\官方\"\n店", "a@t.com", UserRole.merchant, null);
        when(userService.getUserInfo(merchantId)).thenReturn(merchant);

        listener.onActivityApproved(new ActivityApprovedEvent(activityId, merchantId));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(webSocketHandler).broadcast(captor.capture());
        String json = captor.getValue();

        // ObjectMapper 正确转义特殊字符，输出的 JSON 必须可解析
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        assertThat(node.get("type").asText()).isEqualTo("ACTIVITY_ANNOUNCEMENT");
        assertThat(node.get("message").asText()).contains("小米");
    }

    // ========================================================================
    // onActivitySubmittedForReview
    // ========================================================================

    @Test
    void submittedForReview_happyPath_sendsToAdmins() {
        Activity activity = anActivity();
        when(activityMapper.selectById(activityId)).thenReturn(activity);
        when(userService.getUserInfo(merchantId)).thenReturn(new UserInfo(merchantId, "商家A", "a@t.com", UserRole.merchant, null));

        SysUser admin1 = new SysUser(); admin1.setUserId(1L); admin1.setRole(UserRole.admin);
        SysUser admin2 = new SysUser(); admin2.setUserId(2L); admin2.setRole(UserRole.admin);
        // 注：LambdaQueryWrapper.toString() 格式随 MP 版本变动，用 any() 后靠 verify 保障调用
        when(sysUserMapper.selectList(any())).thenReturn(List.of(admin1, admin2));

        listener.onActivitySubmittedForReview(new ActivitySubmittedForReviewEvent(activityId, merchantId));

        verify(userMessageService).sendMessage(eq(1L), eq(MessageType.approval_result), anyString(), eq(activityId));
        verify(userMessageService).sendMessage(eq(2L), eq(MessageType.approval_result), anyString(), eq(activityId));
    }

    @Test
    void submittedForReview_activityNotFound_skips() {
        when(activityMapper.selectById(activityId)).thenReturn(null);

        listener.onActivitySubmittedForReview(new ActivitySubmittedForReviewEvent(activityId, merchantId));

        verify(userMessageService, never()).sendMessage(anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void submittedForReview_noAdmins_skips() {
        when(activityMapper.selectById(activityId)).thenReturn(anActivity());
        when(userService.getUserInfo(merchantId)).thenReturn(new UserInfo(merchantId, "商家A", "a@t.com", UserRole.merchant, null));
        when(sysUserMapper.selectList(any())).thenReturn(List.of());

        listener.onActivitySubmittedForReview(new ActivitySubmittedForReviewEvent(activityId, merchantId));

        verify(userMessageService, never()).sendMessage(anyLong(), any(), anyString(), anyLong());
    }

    // ========================================================================
    // onActivityRejected
    // ========================================================================

    @Test
    void onActivityRejected_sendsMerchantNotification() {
        Activity activity = anActivity();
        when(activityMapper.selectById(activityId)).thenReturn(activity);

        listener.onActivityRejected(new ActivityRejectedEvent(activityId, merchantId, "价格不合理"));

        verify(userMessageService).sendMessage(eq(merchantId),
                eq(com.seckill.module.message.model.enums.MessageType.approval_result),
                anyString(), eq(activityId));
    }

    @Test
    void onActivityRejected_activityNotFound_skips() {
        when(activityMapper.selectById(activityId)).thenReturn(null);

        listener.onActivityRejected(new ActivityRejectedEvent(activityId, merchantId, "原因"));

        verify(userMessageService, never()).sendMessage(anyLong(), any(), anyString(), anyLong());
    }

    // ========================================================================
    // onUserBanned
    // ========================================================================

    @Test
    void userBanned_sendsBanInfo() {
        listener.onUserBanned(new UserBannedEvent(200L, 100L));

        verify(userMessageService).sendMessage(eq(200L), eq(MessageType.ban_info), anyString(), eq(null));
    }

    // ========================================================================
    // onUserUnbanned
    // ========================================================================

    @Test
    void userUnbanned_sendsBanInfo() {
        listener.onUserUnbanned(new UserUnbannedEvent(300L, 100L));

        verify(userMessageService).sendMessage(eq(300L), eq(MessageType.ban_info), anyString(), eq(null));
    }

    @Test
    void userUnbanned_sendMessageThrows_propagatesOut() {
        doThrow(new RuntimeException("DB error"))
                .when(userMessageService).sendMessage(anyLong(), any(), anyString(), any());

        assertThatThrownBy(() ->
                listener.onUserUnbanned(new UserUnbannedEvent(300L, 100L)))
                .isInstanceOf(RuntimeException.class);
    }

    // ========================================================================
    // onUserRegistered
    // ========================================================================

    @Test
    void userRegistered_sendsWelcome() {
        listener.onUserRegistered(new UserRegisteredEvent(400L, "new@test.com", UserRole.user));

        verify(userMessageService).sendMessage(eq(400L), eq(MessageType.welcome), anyString(), eq(null));
    }

    @Test
    void userRegistered_sendMessageThrows_propagatesOut() {
        doThrow(new RuntimeException("DB error"))
                .when(userMessageService).sendMessage(anyLong(), any(), anyString(), any());

        assertThatThrownBy(() ->
                listener.onUserRegistered(new UserRegisteredEvent(400L, "new@test.com", UserRole.user)))
                .isInstanceOf(RuntimeException.class);
    }
}
