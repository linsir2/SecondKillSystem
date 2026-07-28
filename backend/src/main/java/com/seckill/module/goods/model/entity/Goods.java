package com.seckill.module.goods.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 普通商品实体。
 *
 * <p>上下架转换方法内化状态校验。
 * Invalid transition 抛 {@link IllegalStateException}，由 Service catch 转 {@link com.seckill.common.exception.BusinessException}。</p>
 */
@Data
@TableName("goods")
public class Goods {
    @TableId(type = IdType.ASSIGN_ID)
    private Long goodsId;
    private String goodsName;
    private Long merchantId;
    private BigDecimal price;
    private Integer status;
    private Integer stock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ========================================================================
    // 领域行为
    // ========================================================================

    /**
     * 上架（status 0 → 1）。
     *
     * @throws IllegalStateException 当前状态不为 0
     */
    public void putOnSale() {
        if (this.status == null || this.status != 0) {
            throw new IllegalStateException("商品状态异常，无法上架");
        }
        this.status = 1;
    }

    /**
     * 下架（status 1 → 0）。
     *
     * @throws IllegalStateException 当前状态不为 1
     */
    public void takeOffShelf() {
        if (this.status == null || this.status != 1) {
            throw new IllegalStateException("商品状态异常，无法下架");
        }
        this.status = 0;
    }
}
