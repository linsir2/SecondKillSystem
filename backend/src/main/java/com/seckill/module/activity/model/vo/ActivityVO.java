package com.seckill.module.activity.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动返回数据结构。
 */
@Data
@AllArgsConstructor
@Schema(description = "活动视图对象")
public class ActivityVO {
    @Schema(description = "活动 ID")
    private Long activityId;
    @Schema(description = "活动名称")
    private String activityName;
    @Schema(description = "商家 ID")
    private Long merchantId;
    @Schema(description = "活动状态 (draft/pending/preheating/running/ended)")
    private String status;
    @Schema(description = "开始时间")
    private LocalDateTime startTime;
    @Schema(description = "结束时间")
    private LocalDateTime endTime;
    @Schema(description = "活动描述")
    private String description;
    @Schema(description = "秒杀商品列表")
    private List<SeckillGoodsVO> seckillGoodsList;
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    @Schema(description = "驳回理由（可空）；仅管理员驳回后填充")
    private String rejectReason;
}
