package com.seckill.module.message.model.enums;

public enum MessageType {
    approval_result,          // 审核结果（通过/驳回）
    ban_info,                 // 封禁/解封通知
    sent_error,               // 投递失败告警
    welcome,                  // 新用户欢迎
    payment_notify_merchant,  // 支付通知 → 商家（提醒发货）
    payment_notify_user       // 支付通知 → 买家（购买成功）
}
