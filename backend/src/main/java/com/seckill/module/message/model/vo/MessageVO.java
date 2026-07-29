package com.seckill.module.message.model.vo;

import java.time.LocalDateTime;

/**
 * 消息视图对象 —— 避免 entity 直接暴露给前端。
 */
public record MessageVO(
        Long messageId,
        String type,
        String content,
        Long activityId,
        boolean read,
        LocalDateTime createdAt
) {}
