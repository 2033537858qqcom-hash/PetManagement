package com.petcare.grooming.payment;

import com.petcare.grooming.payment.handler.PaymentChannelHandler;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentGateway {
    private final Map<PaymentChannel, PaymentChannelHandler> handlers = new EnumMap<>(PaymentChannel.class);
    private final PaymentProperties properties;

    public PaymentGateway(List<PaymentChannelHandler> handlerList, PaymentProperties properties) {
        this.properties = properties;
        for (PaymentChannelHandler handler : handlerList) {
            handlers.put(handler.channel(), handler);
        }
    }

    public PaymentChannelHandler getHandler(PaymentChannel channel) {
        PaymentChannelHandler handler = handlers.get(channel);
        if (handler == null) {
            throw new IllegalArgumentException("未支持的支付渠道: " + channel);
        }
        return handler;
    }

    public boolean isSupported(PaymentChannel channel) {
        return handlers.containsKey(channel);
    }

    public PaymentProperties getProperties() {
        return properties;
    }
}
