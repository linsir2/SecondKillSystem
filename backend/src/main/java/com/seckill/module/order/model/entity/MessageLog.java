package com.seckill.module.order.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seckill.module.order.model.enums.SendStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表 message_log 对应的实体（事务性 outbox）。
 */
@Data
@TableName("message_log")
public class MessageLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long msgId;
    private String bizType;
    private String bizId;
    private String topic;
    private String tag;
    private String body;
    private SendStatus status;
    private Integer retryCount;
    private LocalDateTime sendTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
