package com.seckill.module.activity.model.enums;

/**
 * 活动状态，与 DB ENUM('draft','pending','preheating','running','ended') 一一对应。
 *
 * <p>状态机: draft → pending → preheating → running → ended
 */
public enum ActivityStatus {
    draft,
    pending,
    preheating,
    running,
    ended
}
