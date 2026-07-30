package com.seckill.module.activity.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @NotBlank
    @Size(max = 64, message = "活动名称不能超过 64 个字符")
    @Schema(description = "活动名称")
    private String activityName;

    @NotNull
    @Schema(description = "活动开始时间")
    private LocalDateTime startTime;

    @NotNull
    @Schema(description = "活动结束时间")
    private LocalDateTime endTime;

    @Schema(description = "活动描述")
    private String description;

    @NotEmpty
    @Valid
    @Schema(description = "秒杀商品列表")
    private List<CreateSeckillGoodsItem> seckillGoodsList;

    @AssertTrue(message = "结束时间必须晚于开始时间")
    @JsonIgnore
    public boolean isEndAfterStart() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
