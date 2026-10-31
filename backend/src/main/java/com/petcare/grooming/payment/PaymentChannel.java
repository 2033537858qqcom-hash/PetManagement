package com.petcare.grooming.payment;

public enum PaymentChannel {
    ALIPAY("ALIPAY", "支付宝");

    private final String code;
    private final String description;

    PaymentChannel(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }

    public static PaymentChannel fromString(String channel) {
        if (channel == null) return null;
        if ("ALIPAY".equalsIgnoreCase(channel.trim())) return ALIPAY;
        return null;
    }
}
