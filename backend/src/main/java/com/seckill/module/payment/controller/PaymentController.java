package com.seckill.module.payment.controller;

import com.seckill.module.payment.model.dto.PayRequest;
import com.seckill.module.payment.model.dto.PayResponse;
import com.seckill.module.payment.service.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付端点。
 */
@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/pay")
    public PayResponse pay(@RequestBody PayRequest request) {
        return paymentService.pay(request);
    }
}
