package com.petcare.grooming.payment.model;

import com.petcare.grooming.payment.PaymentChannel;
import com.petcare.grooming.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentQueryResult(
    String paymentNo,
    PaymentChannel channel,
    PaymentStatus status,
    String tradeNo,
    BigDecimal amount,
    LocalDateTime paidAt,
    String rawResponse
) {}
