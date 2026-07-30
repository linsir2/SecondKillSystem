package com.seckill.module.message.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 消息视图对象 —— 避免 entity 直接暴露给前端。
 */
@Schema(description = "消息视图对象")
public record MessageVO(
        @Schema(description = "消息 ID") Long messageId,
        @Schema(description = "消息类型（approval_result/ban_info/sent_error/welcome）") String type,
        @Schema(description = "消息内容") String content,
        @Schema(description = "关联活动 ID（可空）") Long activityId,
        @Schema(description = "是否已读") boolean read,
        @Schema(description = "创建时间") LocalDateTime createdAt
) {}
