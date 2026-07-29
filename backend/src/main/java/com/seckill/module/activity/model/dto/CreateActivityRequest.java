package com.seckill.module.activity.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建活动请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateActivityRequest {
    private String activityName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private List<CreateSeckillGoodsItem> seckillGoodsList;
}
