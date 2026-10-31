package com.petcare.grooming.payment.model;

import com.petcare.grooming.payment.PaymentChannel;
import com.petcare.grooming.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentOrderResult(
    String paymentNo,
    PaymentChannel channel,
    BigDecimal amount,
    PaymentStatus status,
    String qrCode,
    LocalDateTime expireAt,
    String prepayId
) {}
