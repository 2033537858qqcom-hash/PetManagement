package com.petcare.grooming.payment;

public enum PaymentStatus {
    PENDING("待支付"),
    SUCCESS("支付成功"),
    CLOSED("已关闭"),
    REFUNDED("已退款");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
