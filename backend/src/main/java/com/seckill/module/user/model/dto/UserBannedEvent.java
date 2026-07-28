package com.seckill.module.user.model.dto;

/**
 * 用户已被封禁领域事件 —— banUser() 成功后由 Service 发布。
 *
 * @param userId     被封禁的用户 ID
 * @param operatorId 执行封禁操作的管理员 ID
 */
public record UserBannedEvent(Long userId, Long operatorId) {
}
