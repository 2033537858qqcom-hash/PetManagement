package com.petcare.grooming.payment.handler;

import com.petcare.grooming.payment.PaymentChannel;
import com.petcare.grooming.payment.model.PaymentNotifyResult;
import com.petcare.grooming.payment.model.PaymentOrderResult;
import com.petcare.grooming.payment.model.PaymentQueryResult;
import com.petcare.grooming.payment.model.UnifiedOrderModel;

import java.util.Map;

public interface PaymentChannelHandler {
    PaymentChannel channel();
    PaymentOrderResult createOrder(UnifiedOrderModel order);
    PaymentQueryResult queryOrder(String paymentNo, String tradeNo);
    PaymentNotifyResult parseAndVerifyNotify(String body, Map<String, String> headers, Map<String, String[]> params);
    boolean isLiveConfigured();
}
