package com.seckill.module.order.controller;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.order.model.dto.CancelOrderRequest;
import com.seckill.module.order.model.vo.OrderStatusVO;
import com.seckill.module.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单端点 —— 前端轮询订单状态 + 手动取消订单。
 *
 * <p>前端在抢购成功后获得 {@code orderToken}，轮询 {@code GET /api/v1/order/status}
 * 直到返回非 null 的 {@code status} 和 {@code orderNo}，然后跳转支付页。
 * 支付前可调用 {@code POST /api/v1/order/cancel} 主动取消。</p>
 */
@Tag(name = "订单管理", description = "订单状态查询、取消订单")
@RestController
@RequestMapping("/api/v1/order")
@PreAuthorize("isAuthenticated()")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "订单状态查询", description = "根据 orderToken 轮询订单状态，返回 status 和 orderNo")
    @GetMapping("/status")
    public Result<OrderStatusVO> getStatus(@Parameter(description = "秒杀成功后返回的 orderToken") @RequestParam("token") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        OrderStatusVO vo = orderService.getOrderStatusVO(token, user.getUserId());
        return Result.success(vo);
    }

    @Operation(summary = "取消订单", description = "支付前主动取消订单，恢复库存")
    @PostMapping("/cancel")
    public Result<Void> cancel(@Valid @RequestBody CancelOrderRequest request) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        orderService.cancel(request.orderNo(), user.getUserId());
        return Result.success(null);
    }
}
