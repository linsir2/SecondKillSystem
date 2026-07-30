package com.seckill.module.activity.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 活动驳回请求。
 *
 * @param reason 驳回理由（可选，null/blank 时 Service 层降级为默认理由）
 */
@Schema(description = "活动驳回请求")
public record RejectActivityRequest(@Schema(description = "驳回理由（可选）") String reason) {
}
