package com.seckill.module.payment.controller;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.payment.model.dto.PayRequest;
import com.seckill.module.payment.model.dto.PayResponse;
import com.seckill.module.payment.service.PaymentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付端点 —— 从 JWT 获取用户身份，不信任请求体中的 userId。
 */
@RestController
@RequestMapping("/api/v1/payment")
@PreAuthorize("isAuthenticated()")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/pay")
    public PayResponse pay(@RequestBody PayRequest request) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        // 使用认证用户 ID，忽略请求体中的 userId —— 防止 A 冒用 B 的 userId 探知订单信息
        return paymentService.pay(new PayRequest(request.orderNo(), user.getUserId()));
    }
}
