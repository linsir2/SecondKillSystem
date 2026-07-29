package com.seckill.module.order.controller;

import com.seckill.common.result.Result;
import com.seckill.module.order.model.vo.OrderStatusVO;
import com.seckill.module.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单查询端点 —— 供前端轮询订单状态。
 *
 * <p>前端在抢购成功后获得 {@code orderToken}，轮询 {@code GET /api/v1/order/status}
 * 直到返回非 null 的 {@code status} 和 {@code orderNo}，然后跳转支付页。</p>
 */
@RestController
@RequestMapping("/api/v1/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/status")
    public Result<OrderStatusVO> getStatus(@RequestParam("token") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        OrderStatusVO vo = orderService.getOrderStatusVO(token);
        return Result.success(vo);
    }
}
