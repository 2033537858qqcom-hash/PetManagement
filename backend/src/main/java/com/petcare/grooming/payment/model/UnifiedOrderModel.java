package com.petcare.grooming.payment.model;

import com.petcare.grooming.payment.PaymentChannel;
import java.math.BigDecimal;

public record UnifiedOrderModel(
    String paymentNo,
    PaymentChannel channel,
    BigDecimal amount,
    String description,
    String attach,
    int timeoutMinutes
) {}
