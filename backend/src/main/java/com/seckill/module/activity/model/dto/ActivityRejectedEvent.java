package com.seckill.module.activity.model.dto;

/**
 * 活动已驳回领域事件 —— reject() 成功后由 Service 发布。
 *
 * @param activityId 活动 ID
 * @param merchantId 活动的商家 ID
 * @param reason     驳回理由（已归一化，不会为 null/blank）
 */
public record ActivityRejectedEvent(Long activityId, Long merchantId, String reason) {
}
