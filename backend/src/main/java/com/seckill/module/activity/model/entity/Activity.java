package com.seckill.module.activity.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seckill.module.activity.model.enums.ActivityStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动表 activity 对应的实体。
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
}
