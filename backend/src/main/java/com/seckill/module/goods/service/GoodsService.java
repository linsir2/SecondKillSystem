package com.seckill.module.goods.service;

import com.seckill.module.goods.model.dto.CreateGoodsRequest;
import com.seckill.module.goods.model.dto.GoodsInfo;
import com.seckill.module.goods.model.dto.UpdateGoodsRequest;
import com.seckill.module.goods.model.vo.GoodsVO;

import java.util.List;

/**
 * 商品上下文对外暴露的服务接口（Customer-Supplier 模式）。
 * <p>Activity 等下游上下文通过 {@link #getGoodsInfo} / {@link #getGoodsInfoList}
 * 校验商品存在性与归属。CRUD 和上下架由商家自行管理。</p>
 */
public interface GoodsService {

    // ========================================================================
    // OHS——供下游上下文只读查询
    // ========================================================================

    /**
     * 查询指定商家下某商品的信息。
     *
     * @param goodsId    商品 ID
     * @param merchantId 商家 ID（用于校验归属）
     * @return 商品信息，不存在或不属于该商家时返回 {@code null}
     * @throws IllegalArgumentException 任一参数为 {@code null}
     */
    GoodsInfo getGoodsInfo(Long goodsId, Long merchantId);

    /**
     * 批量查询指定商家下多个商品的信息。
     * <p>顺序与输入一致，不存在的商品对应位置为 {@code null}。</p>
     *
     * @param goodsIds   商品 ID 列表
     * @param merchantId 商家 ID
     * @return 商品信息列表，非 null
     * @throws IllegalArgumentException 任一参数为 {@code null}
     */
    List<GoodsInfo> getGoodsInfoList(List<Long> goodsIds, Long merchantId);

    /**
     * 审核通过时预占秒杀库存（goods.stock -= delta）。
     * <p>原子 SQL 扣减，不做先查后改。失败抛异常。</p>
     *
     * @param goodsId    商品 ID（必填）
     * @param merchantId 商家 ID（校验归属，必填）
     * @param delta      预占数量，须 &gt; 0
     * @throws IllegalArgumentException 任一参数为 null / delta ≤ 0
     * @throws BusinessException       商品不存在或不属于该商家 / 库存不足 / 扣减失败
     */
    void deductStock(Long goodsId, Long merchantId, int delta);

    // ========================================================================
    // CRUD——商家自有商品管理
    // ========================================================================

    /**
     * 商家创建新商品。
     *
     * @param merchantId 商家 ID
     * @param request    创建请求
     * @return 创建后的商品 VO
     * @throws IllegalArgumentException 参数格式非法
     */
    GoodsVO createGoods(Long merchantId, CreateGoodsRequest request);

    /**
     * 商家更新自有商品信息（名称、价格、库存）。
     * <p>不能通过此接口修改 status，上下架请使用 {@link #listGoods} / {@link #delistGoods}。</p>
     *
     * @param merchantId 商家 ID
     * @param goodsId    商品 ID
     * @param request    更新请求
     * @return 更新后的商品 VO
     * @throws IllegalArgumentException   参数格式非法
     * @throws com.seckill.common.exception.BusinessException 商品不存在或不属于该商家
     */
    GoodsVO updateGoods(Long merchantId, Long goodsId, UpdateGoodsRequest request);

    // ========================================================================
    // 上下架
    // ========================================================================

    /**
     * 商家上架自有商品（status → 1）。
     * <p>已上架时幂等返回。</p>
     *
     * @param merchantId 商家 ID
     * @param goodsId    商品 ID
     * @return 操作后的商品 VO
     * @throws com.seckill.common.exception.BusinessException 商品不存在或不属于该商家
     */
    GoodsVO listGoods(Long merchantId, Long goodsId);

    /**
     * 商家下架自有商品（status → 0）。
     * <p>已下架时幂等返回。</p>
     *
     * @param merchantId 商家 ID
     * @param goodsId    商品 ID
     * @return 操作后的商品 VO
     * @throws com.seckill.common.exception.BusinessException 商品不存在或不属于该商家
     */
    GoodsVO delistGoods(Long merchantId, Long goodsId);

    // ========================================================================
    // 列表——供活动创建选品下拉
    // ========================================================================

    /**
     * 查询商家的所有商品（含已下架），按创建时间倒序。
     *
     * @param merchantId 商家 ID
     * @return 商品 VO 列表，非 null
     */
    List<GoodsVO> listMerchantGoods(Long merchantId);
}
