package com.seckill.module.user.model.dto;

/**
 * 用户已解封领域事件 —— unbanUser() 成功后由 Service 发布。
 *
 * @param userId     被解封的用户 ID
 * @param operatorId 执行解封操作的管理员 ID
 */
public record UserUnbannedEvent(Long userId, Long operatorId) {
}
