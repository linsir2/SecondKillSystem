package com.seckill.module.gateway.controller;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.gateway.model.dto.SeckillRequest;
import com.seckill.module.gateway.model.dto.SeckillResponse;
import com.seckill.module.stock.model.dto.SeckillDeductResult;
import com.seckill.module.stock.service.SeckillStockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒杀执行入口。
 *
 * <p>请求经 GatewayCheckFilter 校验后进入此 Controller。
 * 核心职责：调 SeckillStockService.deduct() 完成 Redis+Lua 原子预扣，返回 orderToken。
 */
@RestController
@RequestMapping("/api/v1/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillStockService seckillStockService;

    @PostMapping("/execute")
    public Result<SeckillResponse> execute(@RequestBody @Valid SeckillRequest request) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }

        SeckillDeductResult result = seckillStockService.deduct(
                request.getActivityId(),
                request.getSeckillGoodsId(),
                user.getUserId(),
                request.getBuyCount());

        switch (result.code()) {
            case SeckillDeductResult.CODE_SUCCESS:
                return Result.success(new SeckillResponse(result.orderToken()));
            case SeckillDeductResult.CODE_SOLD_OUT:
                throw new BusinessException("商品已售罄");
            case SeckillDeductResult.CODE_DUPLICATE:
                throw new BusinessException("请勿重复购买");
            case SeckillDeductResult.CODE_OVER_LIMIT:
                throw new BusinessException("超过限购数量");
            default:
                throw new BusinessException("系统繁忙，请重试");
        }
    }
}
