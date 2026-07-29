package com.seckill.module.message.listener;

import com.seckill.module.activity.mapper.ActivityMapper;
import com.seckill.module.activity.model.entity.Activity;
import com.seckill.module.message.model.MessageTemplates;
import com.seckill.module.message.model.enums.MessageType;
import com.seckill.module.message.service.UserMessageService;
import com.seckill.module.order.mapper.SeckillOrderMapper;
import com.seckill.module.order.model.entity.SeckillOrder;
import com.seckill.module.payment.model.dto.PaymentConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 支付确认通知监听器 —— 监听 {@link PaymentConfirmedEvent}，发送商家发货提醒 + 买家购买成功通知。
 *
 * <p>使用 {@link TransactionPhase#AFTER_COMMIT}，确保 DB 事务提交后再发通知。
 * 通知发送是 best-effort 操作，单项失败不影响其他通知。</p>
 */
@Component
public class PaymentConfirmedEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmedEventListener.class);

    private final SeckillOrderMapper orderMapper;
    private final ActivityMapper activityMapper;
    private final UserMessageService userMessageService;

    public PaymentConfirmedEventListener(SeckillOrderMapper orderMapper,
                                         ActivityMapper activityMapper,
                                         UserMessageService userMessageService) {
        this.orderMapper = orderMapper;
        this.activityMapper = activityMapper;
        this.userMessageService = userMessageService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentConfirmed(PaymentConfirmedEvent event) {
        if (event == null) {
            log.warn("Received null PaymentConfirmedEvent, skipping");
            return;
        }
        if (event.orderNo() == null) {
            log.warn("PaymentConfirmedEvent.orderNo is null, skipping");
            return;
        }

        // ---- 1. 查订单 ----
        SeckillOrder order = orderMapper.selectById(event.orderNo());
        if (order == null) {
            log.warn("Order {} not found for payment notification, skipping", event.orderNo());
            return;
        }

        // ---- 2. 查活动（获取商家 ID） ----
        Long merchantId = null;
        Long activityId = order.getActivityId();
        if (activityId != null) {
            Activity activity = activityMapper.selectById(activityId);
            if (activity != null) {
                merchantId = activity.getMerchantId();
            } else {
                log.warn("Activity {} not found for payment notification, skipping merchant notify", activityId);
            }
        }

        String amountStr = event.amount() != null ? event.amount().toPlainString() : "0.00";

        // ---- 3. 商家通知（可降级） ----
        if (merchantId != null) {
            try {
                String merchantContent = MessageTemplates.paymentNotifyMerchant(event.orderNo(), amountStr);
                userMessageService.sendMessage(merchantId, MessageType.payment_notify_merchant, merchantContent, activityId);
            } catch (Exception e) {
                log.error("Failed to send payment notification to merchant {} for order {}",
                        merchantId, event.orderNo(), e);
            }
        }

        // ---- 4. 买家通知 ----
        try {
            String userContent = MessageTemplates.paymentNotifyUser(event.orderNo(), amountStr);
            userMessageService.sendMessage(event.userId(), MessageType.payment_notify_user, userContent, activityId);
        } catch (Exception e) {
            log.error("Failed to send payment notification to user {} for order {}",
                    event.userId(), event.orderNo(), e);
        }
    }
}
