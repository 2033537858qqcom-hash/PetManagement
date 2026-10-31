package com.petcare.grooming.payment.model;

import java.math.BigDecimal;

public record PaymentNotifyResult(
    boolean success,
    String paymentNo,
    String tradeNo,
    BigDecimal amount,
    String message,
    String responseContent,
    String contentType
) {
    public static PaymentNotifyResult ok(String paymentNo, String tradeNo, BigDecimal amount, String responseContent, String contentType) {
        return new PaymentNotifyResult(true, paymentNo, tradeNo, amount, "SUCCESS", responseContent, contentType);
    }

    public static PaymentNotifyResult fail(String message, String responseContent, String contentType) {
        return new PaymentNotifyResult(false, null, null, null, message, responseContent, contentType);
    }
}
