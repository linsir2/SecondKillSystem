package com.seckill.module.message.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.constant.UserRole;
import com.seckill.module.activity.mapper.ActivityMapper;
import com.seckill.module.activity.mapper.SeckillGoodsMapper;
import com.seckill.module.activity.model.dto.ActivityApprovedEvent;
import com.seckill.module.activity.model.dto.ActivityRejectedEvent;
import com.seckill.module.activity.model.dto.ActivitySubmittedForReviewEvent;
import com.seckill.module.activity.model.entity.Activity;
import com.seckill.module.activity.model.entity.SeckillGoods;
import com.seckill.module.goods.model.dto.GoodsInfo;
import com.seckill.module.goods.service.GoodsService;
import com.seckill.module.message.model.MessageTemplates;
import com.seckill.module.message.model.enums.MessageType;
import com.seckill.module.message.service.UserMessageService;
import com.seckill.module.message.websocket.NotificationWebSocketHandler;
import com.seckill.module.user.mapper.SysUserMapper;
import com.seckill.module.user.model.dto.UserBannedEvent;
import com.seckill.module.user.model.dto.UserRegisteredEvent;
import com.seckill.module.user.model.dto.UserUnbannedEvent;
import com.seckill.module.user.model.entity.SysUser;
import com.seckill.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MessageEventListener {

    private static final Logger log = LoggerFactory.getLogger(MessageEventListener.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ActivityMapper activityMapper;
    private final SeckillGoodsMapper seckillGoodsMapper;
    private final GoodsService goodsService;
    private final UserMessageService userMessageService;
    private final SysUserMapper sysUserMapper;
    private final UserService userService;
    private final NotificationWebSocketHandler webSocketHandler;

    /**
     * 活动审核通过 → 商家点对点通知 + WebSocket 用户广播。
     * <p>在发布者事务提交后才执行（AFTER_COMMIT），通知失败不影晌审核事务。</p>
     * <p>重试 3 次（首次 + 2 次回退），仍失败则 log 告警。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 200))
    public void onActivityApproved(ActivityApprovedEvent event) {
        Activity activity = activityMapper.selectById(event.activityId());
        if (activity == null) {
            log.warn("Activity {} not found, skip approval notification", event.activityId());
            return;
        }

        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(
                new LambdaQueryWrapper<SeckillGoods>()
                        .eq(SeckillGoods::getActivityId, event.activityId()));

        int goodsCount = seckillGoodsList.size();

        // ---- 点对点：商家审核通知 ----
        String content = MessageTemplates.merchantApproved(
                activity.getActivityName(),
                activity.getStartTime(),
                goodsCount);

        userMessageService.sendMessage(event.merchantId(), MessageType.approval_result, content, event.activityId());

        // ---- WebSocket 广播：用户预告 ----
        try {
            String merchantName = userService.getUserInfo(event.merchantId()).getUserName();
            String goodsNames = "";
            if (goodsCount > 0) {
                List<Long> goodsIds = seckillGoodsList.stream()
                        .map(SeckillGoods::getGoodsId)
                        .toList();
                List<GoodsInfo> infos = goodsService.getGoodsInfoList(goodsIds, event.merchantId());
                goodsNames = infos.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(GoodsInfo::getGoodsName)
                        .collect(java.util.stream.Collectors.joining("、"));
            }

            String broadcastMsg = MessageTemplates.userAnnouncement(
                    merchantName, activity.getActivityName(), activity.getStartTime(), goodsNames);
            Map<String, String> payload = new HashMap<>();
            payload.put("type", "ACTIVITY_ANNOUNCEMENT");
            payload.put("message", broadcastMsg);
            webSocketHandler.broadcast(MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed for activity {}", event.activityId(), e);
        }
    }

    /**
     * 活动提交审核 → 管理员通知。
     * <p>查所有 admin 用户，每人发一条审核提醒。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 200))
    public void onActivitySubmittedForReview(ActivitySubmittedForReviewEvent event) {
        Activity activity = activityMapper.selectById(event.activityId());
        if (activity == null) {
            log.warn("Activity {} not found, skip review notification", event.activityId());
            return;
        }

        String merchantName = userService.getUserInfo(event.merchantId()).getUserName();
        String content = MessageTemplates.merchantSubmittedForReview(merchantName, activity.getActivityName());

        List<SysUser> admins = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, UserRole.admin));

        for (SysUser admin : admins) {
            userMessageService.sendMessage(admin.getUserId(), MessageType.approval_result, content, event.activityId());
        }
    }

    /**
     * 活动驳回 → 商家通知。
     * <p>不携带具体理由，商家从活动详情页查看。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 200))
    public void onActivityRejected(ActivityRejectedEvent event) {
        Activity activity = activityMapper.selectById(event.activityId());
        if (activity == null) {
            log.warn("Activity {} not found, skip rejection notification", event.activityId());
            return;
        }
        String content = MessageTemplates.merchantRejected(activity.getActivityName());
        userMessageService.sendMessage(event.merchantId(), MessageType.approval_result, content, event.activityId());
    }

    /**
     * 用户被封禁 → 封禁通知。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 200))
    public void onUserBanned(UserBannedEvent event) {
        String content = MessageTemplates.userBanned();
        userMessageService.sendMessage(event.userId(), MessageType.ban_info, content, null);
    }

    /**
     * 用户被解封 → 解封通知。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 200))
    public void onUserUnbanned(UserUnbannedEvent event) {
        String content = MessageTemplates.userUnbanned();
        userMessageService.sendMessage(event.userId(), MessageType.ban_info, content, null);
    }

    /**
     * 用户注册成功 → 欢迎通知。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        String content = MessageTemplates.userWelcome(event.email());
        userMessageService.sendMessage(event.userId(), MessageType.welcome, content, null);
    }

    // ========================================================================
    // Retry fallback
    // ========================================================================

    @Recover
    public void recoverActivityApproved(Exception e, ActivityApprovedEvent event) {
        log.error("Activity approved notification failed after retries, activityId={}", event.activityId(), e);
    }

    @Recover
    public void recoverSubmittedForReview(Exception e, ActivitySubmittedForReviewEvent event) {
        log.error("Activity review notification failed after retries, activityId={}", event.activityId(), e);
    }

    @Recover
    public void recoverUserBanned(Exception e, UserBannedEvent event) {
        log.error("User banned notification failed after retries, userId={}", event.userId(), e);
    }

    @Recover
    public void recoverUserUnbanned(Exception e, UserUnbannedEvent event) {
        log.error("User unbanned notification failed after retries, userId={}", event.userId(), e);
    }

    @Recover
    public void recoverActivityRejected(Exception e, ActivityRejectedEvent event) {
        log.error("Activity rejected notification failed after retries, activityId={}", event.activityId(), e);
    }
}
