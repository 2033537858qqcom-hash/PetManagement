package com.petcare.grooming.payment.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.grooming.payment.AlipayOpenClient;
import com.petcare.grooming.payment.PaymentChannel;
import com.petcare.grooming.payment.PaymentProperties;
import com.petcare.grooming.payment.PaymentStatus;
import com.petcare.grooming.payment.model.PaymentNotifyResult;
import com.petcare.grooming.payment.model.PaymentOrderResult;
import com.petcare.grooming.payment.model.PaymentQueryResult;
import com.petcare.grooming.payment.model.UnifiedOrderModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AlipayChannelHandler implements PaymentChannelHandler {
    private final PaymentProperties properties;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final AlipayOpenClient client;

    public AlipayChannelHandler(PaymentProperties properties, Clock clock, ObjectMapper mapper) {
        this.properties = properties;
        this.clock = clock;
        this.mapper = mapper;
        PaymentProperties.AlipayConfig alipay = properties.getAlipay();
        if ((alipay.getNotifyUrl() == null || alipay.getNotifyUrl().isBlank()) && properties.getNotifyBaseUrl() != null) {
            alipay.setNotifyUrl(properties.getNotifyBaseUrl().replaceAll("/$", "") + "/alipay");
        }
        this.client = new AlipayOpenClient(alipay, mapper);
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.ALIPAY;
    }

    @Override
    public PaymentOrderResult createOrder(UnifiedOrderModel order) {
        LocalDateTime expireAt = LocalDateTime.now(clock).plusMinutes(order.timeoutMinutes() > 0 ? order.timeoutMinutes() : 15);
        if (client.configured()) {
            Map<String, Object> biz = new LinkedHashMap<>();
            biz.put("out_trade_no", order.paymentNo());
            biz.put("total_amount", order.amount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            biz.put("subject", order.description());
            biz.put("timeout_express", Math.max(1, order.timeoutMinutes()) + "m");
            JsonNode result = client.execute("alipay.trade.precreate", biz);
            String qrCode = result.path("qr_code").asText();
            if (qrCode == null || qrCode.isBlank()) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "支付宝未返回收款码");
            }
            return new PaymentOrderResult(order.paymentNo(), PaymentChannel.ALIPAY, order.amount(), PaymentStatus.PENDING, qrCode, expireAt, result.path("out_trade_no").asText(order.paymentNo()));
        }
        String code = "bax" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new PaymentOrderResult(order.paymentNo(), PaymentChannel.ALIPAY, order.amount(), PaymentStatus.PENDING, "https://qr.alipay.com/" + code, expireAt, code);
    }

    @Override
    public PaymentQueryResult queryOrder(String paymentNo, String tradeNo) {
        if (client.configured()) {
            Map<String, Object> biz = new LinkedHashMap<>();
            biz.put("out_trade_no", paymentNo);
            if (tradeNo != null && !tradeNo.isBlank()) biz.put("trade_no", tradeNo);
            JsonNode result = client.execute("alipay.trade.query", biz);
            String statusText = result.path("trade_status").asText();
            PaymentStatus status = ("TRADE_SUCCESS".equals(statusText) || "TRADE_FINISHED".equals(statusText))
                ? PaymentStatus.SUCCESS
                : ("TRADE_CLOSED".equals(statusText) ? PaymentStatus.CLOSED : PaymentStatus.PENDING);
            BigDecimal amount = AlipayOpenClient.money(result, "total_amount", "receipt_amount");
            return new PaymentQueryResult(paymentNo, PaymentChannel.ALIPAY, status, result.path("trade_no").asText(tradeNo), amount == null ? BigDecimal.ZERO : amount, null, result.toString());
        }
        return new PaymentQueryResult(paymentNo, PaymentChannel.ALIPAY, PaymentStatus.PENDING, tradeNo != null ? tradeNo : "ALI_TX_" + paymentNo, BigDecimal.ZERO, null, "{\"trade_status\":\"WAIT_BUYER_PAY\"}");
    }

    @Override
    public PaymentNotifyResult parseAndVerifyNotify(String body, Map<String, String> headers, Map<String, String[]> params) {
        Map<String, String> flat = flatten(params, body);
        if (client.configured() && !client.verifyNotify(flat)) {
            return PaymentNotifyResult.fail("支付宝回调验签失败", "fail", "text/plain");
        }
        String paymentNo = first(flat, "out_trade_no", "paymentNo");
        String tradeNo = first(flat, "trade_no", "tradeNo");
        String amountText = first(flat, "total_amount", "amount");
        if (paymentNo == null || paymentNo.isBlank()) {
            return PaymentNotifyResult.fail("缺少商户订单号 out_trade_no", "fail", "text/plain");
        }
        String tradeStatus = first(flat, "trade_status");
        if (tradeStatus != null && !"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus) && !"WAIT_BUYER_PAY".equals(tradeStatus) && client.configured()) {
            return PaymentNotifyResult.fail("交易未成功: " + tradeStatus, "success", "text/plain");
        }
        BigDecimal amount = null;
        try { if (amountText != null) amount = new BigDecimal(amountText); } catch (Exception ignored) {}
        if (tradeNo == null || tradeNo.isBlank()) tradeNo = "ALI_" + paymentNo;
        return PaymentNotifyResult.ok(paymentNo, tradeNo, amount, "success", "text/plain");
    }

    @Override
    public boolean isLiveConfigured() {
        return client.configured();
    }

    private Map<String, String> flatten(Map<String, String[]> params, String body) {
        Map<String, String> flat = new java.util.LinkedHashMap<>();
        if (params != null) {
            for (var e : params.entrySet()) {
                if (e.getValue() != null && e.getValue().length > 0) flat.put(e.getKey(), e.getValue()[0]);
            }
        }
        if (body != null && !body.isBlank()) {
            if (body.trim().startsWith("{")) {
                try {
                    JsonNode root = mapper.readTree(body);
                    root.fields().forEachRemaining(e -> { if (!e.getValue().isContainerNode()) flat.putIfAbsent(e.getKey(), e.getValue().asText()); });
                } catch (Exception ignored) {}
            } else {
                for (String part : body.split("&")) {
                    String[] pair = part.split("=", 2);
                    if (pair.length == 2) {
                        try { flat.putIfAbsent(pair[0], java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8)); }
                        catch (Exception ignored) { flat.putIfAbsent(pair[0], pair[1]); }
                    }
                }
            }
        }
        return flat;
    }

    private static String first(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String value = map.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
