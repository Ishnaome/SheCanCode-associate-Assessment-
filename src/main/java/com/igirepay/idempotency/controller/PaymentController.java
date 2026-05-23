package com.igirepay.idempotency.controller;

import com.igirepay.idempotency.model.Models.PaymentRequest;
import com.igirepay.idempotency.model.Models.PaymentResponse;
import com.igirepay.idempotency.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process-payment")
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequest request) {
            
        return paymentService.processPayment(idempotencyKey, request);
    }
}