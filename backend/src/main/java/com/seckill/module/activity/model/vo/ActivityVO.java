package com.seckill.module.activity.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动返回数据结构。
 */
@Data
@AllArgsConstructor
public class ActivityVO {
    private Long activityId;
    private String activityName;
    private Long merchantId;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private List<SeckillGoodsVO> seckillGoodsList;
    private LocalDateTime createdAt;
}
