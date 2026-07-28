package com.seckill.module.goods.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.goods.mapper.GoodsMapper;
import com.seckill.module.goods.model.dto.CreateGoodsRequest;
import com.seckill.module.goods.model.dto.GoodsInfo;
import com.seckill.module.goods.model.dto.UpdateGoodsRequest;
import com.seckill.module.goods.model.entity.Goods;
import com.seckill.module.goods.model.vo.GoodsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoodsServiceImpl implements GoodsService {

    private final GoodsMapper goodsMapper;

    // ========================================================================
    // OHS
    // ========================================================================

    @Override
    public GoodsInfo getGoodsInfo(Long goodsId, Long merchantId) {
        if (goodsId == null) throw new IllegalArgumentException("goodsId must not be null");
        if (merchantId == null) throw new IllegalArgumentException("merchantId must not be null");

        Goods goods = goodsMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Goods>()
                        .eq(Goods::getGoodsId, goodsId)
                        .eq(Goods::getMerchantId, merchantId));
        return toGoodsInfo(goods);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<GoodsInfo> getGoodsInfoList(List<Long> goodsIds, Long merchantId) {
        if (goodsIds == null) throw new IllegalArgumentException("goodsIds must not be null");
        if (merchantId == null) throw new IllegalArgumentException("merchantId must not be null");
        if (goodsIds.isEmpty()) return Collections.emptyList();

        List<Goods> goodsList = goodsMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Goods>()
                        .in(Goods::getGoodsId, goodsIds)
                        .eq(Goods::getMerchantId, merchantId));

        Map<Long, Goods> map = goodsList.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(u -> u.getGoodsId(), Function.identity(), (a, b) -> a));

        return goodsIds.stream()
                .map(id -> toGoodsInfo(map.get(id)))
                .collect(Collectors.toList());
    }

    @Override
    public void deductStock(Long goodsId, Long merchantId, int delta) {
        if (goodsId == null) throw new IllegalArgumentException("goodsId must not be null");
        if (merchantId == null) throw new IllegalArgumentException("merchantId must not be null");
        if (delta <= 0) throw new IllegalArgumentException("delta must be positive");

        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null || !goods.getMerchantId().equals(merchantId)) {
            throw new BusinessException("商品不存在或不属于该商家");
        }
        if (goods.getStock() < delta) {
            throw new BusinessException("《" + goods.getGoodsName() + "》库存不足");
        }

        int affected = goodsMapper.deductStock(goodsId, delta);
        if (affected == 0) {
            throw new BusinessException("库存扣减失败");
        }
    }

    // ========================================================================
    // CRUD
    // ========================================================================

    @Override
    public GoodsVO createGoods(Long merchantId, CreateGoodsRequest request) {
        if (merchantId == null) throw new IllegalArgumentException("merchantId must not be null");
        validateCreateRequest(request);

        Goods goods = new Goods();
        goods.setGoodsName(request.getGoodsName().trim());
        goods.setMerchantId(merchantId);
        goods.setPrice(request.getPrice());
        goods.setStatus(1);
        goods.setStock(request.getStock());
        goods.setCreatedAt(LocalDateTime.now());

        goodsMapper.insert(goods);
        return toGoodsVO(goods);
    }

    @Override
    public GoodsVO updateGoods(Long merchantId, Long goodsId, UpdateGoodsRequest request) {
        if (merchantId == null) throw new IllegalArgumentException("merchantId must not be null");
        if (goodsId == null) throw new IllegalArgumentException("goodsId must not be null");
        validateUpdateRequest(request);

        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) throw new BusinessException("商品不存在");
        if (!goods.getMerchantId().equals(merchantId)) throw new BusinessException("无权操作该商品");

        goods.setGoodsName(request.getGoodsName().trim());
        goods.setPrice(request.getPrice());
        goods.setStock(request.getStock());
        goodsMapper.updateById(goods);

        return toGoodsVO(goods);
    }

    // ========================================================================
    // 上下架
    // ========================================================================

    @Override
    public GoodsVO listGoods(Long merchantId, Long goodsId) {
        Goods goods = findOwnedGoods(merchantId, goodsId);
        if (goods.getStatus() == 1) return toGoodsVO(goods); // 幂等

        goods.setStatus(1);
        goodsMapper.updateById(goods);
        return toGoodsVO(goods);
    }

    @Override
    public GoodsVO delistGoods(Long merchantId, Long goodsId) {
        Goods goods = findOwnedGoods(merchantId, goodsId);
        if (goods.getStatus() == 0) return toGoodsVO(goods); // 幂等

        goods.setStatus(0);
        goodsMapper.updateById(goods);
        return toGoodsVO(goods);
    }

    // ========================================================================
    // 商家列表
    // ========================================================================

    @Override
    public List<GoodsVO> listMerchantGoods(Long merchantId) {
        List<Goods> list = goodsMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Goods>()
                        .eq(Goods::getMerchantId, merchantId)
                        .orderByDesc(Goods::getCreatedAt)
                        .orderByDesc(Goods::getGoodsId));
        return list.stream().map(this::toGoodsVO).collect(Collectors.toList());
    }

    // ========================================================================
    // 校验
    // ========================================================================

    private void validateCreateRequest(CreateGoodsRequest r) {
        validateName(r.getGoodsName());
        if (r.getPrice() == null) throw new IllegalArgumentException("价格不能为空");
        if (r.getPrice().compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("价格必须大于0");
        if (r.getStock() == null) throw new IllegalArgumentException("库存不能为空");
        if (r.getStock() < 0) throw new IllegalArgumentException("库存不能为负数");
    }

    private void validateUpdateRequest(UpdateGoodsRequest r) {
        validateName(r.getGoodsName());
        if (r.getPrice() == null) throw new IllegalArgumentException("价格不能为空");
        if (r.getPrice().compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("价格必须大于0");
        if (r.getStock() == null) throw new IllegalArgumentException("库存不能为空");
        if (r.getStock() < 0) throw new IllegalArgumentException("库存不能为负数");
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("商品名不能为空");
        if (name.trim().length() > 255) throw new IllegalArgumentException("商品名不能超过255个字符");
    }

    /** 查找并校验商品归属。不存在或不属于当前商家时抛 BusinessException。 */
    private Goods findOwnedGoods(Long merchantId, Long goodsId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) throw new BusinessException("商品不存在");
        if (!goods.getMerchantId().equals(merchantId)) throw new BusinessException("无权操作该商品");
        return goods;
    }

    // ========================================================================
    // 转换
    // ========================================================================

    private GoodsInfo toGoodsInfo(Goods entity) {
        if (entity == null) return null;
        return new GoodsInfo(entity.getGoodsId(), entity.getGoodsName(),
                entity.getPrice(), entity.getStock());
    }

    private GoodsVO toGoodsVO(Goods entity) {
        return new GoodsVO(entity.getGoodsId(), entity.getGoodsName(),
                entity.getPrice(), entity.getStatus(), entity.getStock(), entity.getCreatedAt());
    }
}
