package com.seckill.module.activity.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seckill.module.activity.model.enums.ActivityStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动表 activity 对应的实体。
 *
 * <p>状态转换方法内化领域校验，Service 层不再直接 {@code setStatus}。
 * Invalid transition 抛 {@link IllegalStateException}，由 Service catch 转 {@link com.seckill.common.exception.BusinessException}。</p>
 */
@Data
@TableName("activity")
public class Activity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long activityId;
    private String activityName;
    private Long merchantId;
    private ActivityStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ========================================================================
    // 领域行为
    // ========================================================================

    /**
     * draft → pending（提交审核）。
     *
     * @throws IllegalStateException 当前状态不是 draft
     */
    public void submitForReview() {
        if (this.status != ActivityStatus.draft) {
            throw new IllegalStateException("当前状态不可提交审核");
        }
        this.status = ActivityStatus.pending;
    }

    /**
     * pending → preheating（审核通过）。
     *
     * @throws IllegalStateException 当前状态不是 pending
     */
    public void approve() {
        if (this.status != ActivityStatus.pending) {
            throw new IllegalStateException("当前状态不可审核");
        }
        this.status = ActivityStatus.preheating;
    }

    /**
     * preheating → running（活动开始）。
     *
     * @throws IllegalStateException 当前状态不是 preheating
     */
    public void start() {
        if (this.status != ActivityStatus.preheating) {
            throw new IllegalStateException("当前状态不可开始");
        }
        this.status = ActivityStatus.running;
    }
}
