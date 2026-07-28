package com.seckill.module.activity.model.dto;

/**
 * 活动已审核通过领域事件 —— approve() 成功后由 Service 发布。
 *
 * @param activityId 活动 ID
 * @param merchantId 活动的商家 ID
 */
public record ActivityApprovedEvent(Long activityId, Long merchantId) {
}
