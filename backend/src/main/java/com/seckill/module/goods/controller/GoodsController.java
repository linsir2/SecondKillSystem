package com.seckill.module.goods.controller;

import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.goods.model.dto.CreateGoodsRequest;
import com.seckill.module.goods.model.dto.GoodsInfo;
import com.seckill.module.goods.model.dto.UpdateGoodsRequest;
import com.seckill.module.goods.model.vo.GoodsVO;
import com.seckill.module.goods.service.GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家商品 REST 接口 —— CRUD、上下架、列表、详情。
 *
 * <p>所有端点需商家角色，商家 ID 从 {@link SecurityContext} 自动注入，
 * 避免商家篡改 path 中的 merchantId 操作他人商品。</p>
 */
@RestController
@RequestMapping("/api/v1/goods")
@RequiredArgsConstructor
@PreAuthorize("hasRole('merchant')")
public class GoodsController {

    private final GoodsService goodsService;

    /**
     * 商家创建自有商品。
     */
    @PostMapping
    public Result<GoodsVO> createGoods(@RequestBody CreateGoodsRequest request) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.merchant) {
            throw new BusinessException("仅商家可操作");
        }
        return Result.success(goodsService.createGoods(user.getUserId(), request));
    }

    /**
     * 商家更新自有商品信息。
     */
    @PutMapping("/{goodsId}")
    public Result<GoodsVO> updateGoods(@PathVariable Long goodsId,
                                       @RequestBody UpdateGoodsRequest request) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.merchant) {
            throw new BusinessException("仅商家可操作");
        }
        return Result.success(goodsService.updateGoods(user.getUserId(), goodsId, request));
    }

    /**
     * 商家上架自有商品（幂等）。
     */
    @PostMapping("/{goodsId}/list")
    public Result<GoodsVO> listGoods(@PathVariable Long goodsId) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.merchant) {
            throw new BusinessException("仅商家可操作");
        }
        return Result.success(goodsService.listGoods(user.getUserId(), goodsId));
    }

    /**
     * 商家下架自有商品（幂等）。
     */
    @PostMapping("/{goodsId}/delist")
    public Result<GoodsVO> delistGoods(@PathVariable Long goodsId) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.merchant) {
            throw new BusinessException("仅商家可操作");
        }
        return Result.success(goodsService.delistGoods(user.getUserId(), goodsId));
    }

    /**
     * 商家查看自己的全部商品列表（含已下架）。
     */
    @GetMapping
    public Result<List<GoodsVO>> listMerchantGoods() {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.merchant) {
            throw new BusinessException("仅商家可操作");
        }
        return Result.success(goodsService.listMerchantGoods(user.getUserId()));
    }

    /**
     * 商家查看自有商品详情。
     */
    @GetMapping("/{goodsId}")
    public Result<GoodsInfo> getGoodsInfo(@PathVariable Long goodsId) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.merchant) {
            throw new BusinessException("仅商家可操作");
        }
        return Result.success(goodsService.getGoodsInfo(goodsId, user.getUserId()));
    }
}
