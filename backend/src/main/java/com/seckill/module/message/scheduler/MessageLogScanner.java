package com.seckill.module.message.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.module.order.mapper.MessageLogMapper;
import com.seckill.module.order.model.dto.OrderTimeoutMessage;
import com.seckill.module.order.model.entity.MessageLog;
import com.seckill.module.order.model.enums.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表扫描线程 —— 每隔 8 秒扫描 message_log 中 status=INIT 的记录，
 * 发送延时关单消息到 RocketMQ，并按结果更新状态。
 *
 * <p>成功 → SENT + sendTime<br>
 * 失败 → retry_count++；retry_count >= 3 → FAIL</p>
 */
@Component
public class MessageLogScanner {

    private static final Logger log = LoggerFactory.getLogger(MessageLogScanner.class);

    private final MessageLogMapper messageLogMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 20;
    private static final long MQ_TIMEOUT_MS = 3000;

    public MessageLogScanner(MessageLogMapper messageLogMapper,
                             @Autowired(required = false) RocketMQTemplate rocketMQTemplate) {
        this.messageLogMapper = messageLogMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Scheduled(fixedRate = 8000)
    public void scanAndSend() {
        if (rocketMQTemplate == null) {
            return; // MQ 未配置
        }

        try {
            List<MessageLog> logs = messageLogMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MessageLog>()
                            .eq("status", SendStatus.INIT)
                            .lt("retry_count", 3)
                            .orderByAsc("created_at")
                            .last("LIMIT " + BATCH_SIZE));

            for (MessageLog logEntry : logs) {
                processLog(logEntry);
            }
        } catch (Exception e) {
            log.error("scanAndSend failed, will retry next cycle", e);
        }
    }

    private void processLog(MessageLog logEntry) {
        try {
            if (logEntry.getBody() == null || logEntry.getBody().isBlank()) {
                log.warn("message_log body is null/blank, marking FAIL, msgId={}", logEntry.getMsgId());
                markFailed(logEntry);
                return;
            }

            OrderTimeoutMessage msg = objectMapper.readValue(logEntry.getBody(), OrderTimeoutMessage.class);

            rocketMQTemplate.syncSend(
                    logEntry.getTopic() + ":" + logEntry.getTag(),
                    msg,
                    MQ_TIMEOUT_MS);

            // 成功
            MessageLog update = new MessageLog();
            update.setMsgId(logEntry.getMsgId());
            update.setStatus(SendStatus.SENT);
            update.setSendTime(LocalDateTime.now());
            messageLogMapper.updateById(update);

        } catch (Exception e) {
            // 失败：自增 retry_count（独立 try-catch，避免二次异常传播到 scanAndSend 之外）
            try {
                int newRetry = logEntry.getRetryCount() == null ? 1 : logEntry.getRetryCount() + 1;

                MessageLog update = new MessageLog();
                update.setMsgId(logEntry.getMsgId());
                update.setRetryCount(newRetry);
                if (newRetry >= 3) {
                    update.setStatus(SendStatus.FAIL);
                    log.warn("message_log exhausted retries, marking FAIL, msgId={}", logEntry.getMsgId());
                }
                messageLogMapper.updateById(update);
            } catch (Exception inner) {
                log.error("Failed to update retry_count for msgId={}", logEntry.getMsgId(), inner);
            }
        }
    }

    private void markFailed(MessageLog logEntry) {
        MessageLog update = new MessageLog();
        update.setMsgId(logEntry.getMsgId());
        update.setRetryCount(3);
        update.setStatus(SendStatus.FAIL);
        messageLogMapper.updateById(update);
    }
}
