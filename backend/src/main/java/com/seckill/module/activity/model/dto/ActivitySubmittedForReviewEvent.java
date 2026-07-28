package com.seckill.module.activity.model.dto;

/**
 * 活动已提交审核领域事件 —— submitForReview() 成功后由 Service 发布。
 *
 * @param activityId 活动 ID
 * @param merchantId 提交活动的商家 ID
 */
public record ActivitySubmittedForReviewEvent(Long activityId, Long merchantId) {
}
