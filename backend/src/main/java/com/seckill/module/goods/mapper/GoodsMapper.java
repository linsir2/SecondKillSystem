package com.seckill.module.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.module.goods.model.entity.Goods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {

    /**
     * 原子扣减库存。
     *
     * @param goodsId 商品 ID
     * @param delta   扣减数量
     * @return 受影响行数（0 表示库存不足或 ID 不存在）
     */
    @Update("UPDATE goods SET stock = stock - #{delta} WHERE goods_id = #{goodsId} AND stock >= #{delta}")
    int deductStock(@Param("goodsId") Long goodsId, @Param("delta") int delta);

    /**
     * 原子回补库存（活动结束后归还未售库存）。
     *
     * @param goodsId 商品 ID
     * @param delta   回补数量
     * @return 受影响行数（0 表示商品不存在）
     */
    @Update("UPDATE goods SET stock = stock + #{delta} WHERE goods_id = #{goodsId}")
    int addStock(@Param("goodsId") Long goodsId, @Param("delta") int delta);
}
