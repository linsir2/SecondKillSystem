package com.seckill.module.activity.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "创建活动请求体")
public class CreateActivityRequest {
    @Schema(description = "活动名称")
    private String activityName;
    @Schema(description = "活动开始时间")
    private LocalDateTime startTime;
    @Schema(description = "活动结束时间")
    private LocalDateTime endTime;
    @Schema(description = "活动描述")
    private String description;
    @Schema(description = "秒杀商品列表")
    private List<CreateSeckillGoodsItem> seckillGoodsList;
}
