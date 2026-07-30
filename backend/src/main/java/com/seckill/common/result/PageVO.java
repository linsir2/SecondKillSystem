package com.seckill.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页返回结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通用分页返回结构")
public class PageVO<T> {
    @Schema(description = "当前页数据列表")
    private List<T> records;
    @Schema(description = "符合条件的数据总数")
    private long total;
    @Schema(description = "当前页码")
    private int page;
    @Schema(description = "每页条数")
    private int pageSize;
}
