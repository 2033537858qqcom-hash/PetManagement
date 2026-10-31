package com.petcare.grooming.payment;

import com.petcare.grooming.catalog.ServicePricing;
import com.petcare.grooming.store.AuditService;
import com.petcare.grooming.store.NotificationService;
import com.petcare.grooming.payment.handler.PaymentChannelHandler;
import com.petcare.grooming.payment.model.PaymentNotifyResult;
import com.petcare.grooming.payment.model.PaymentOrderResult;
import com.petcare.grooming.payment.model.UnifiedOrderModel;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {
    private static final DateTimeFormatter STAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_NUM_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate db;
    private final Clock clock;
    private final PaymentGateway gateway;
    private final ServicePricing pricing;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public PaymentService(JdbcTemplate db, Clock clock, PaymentGateway gateway, ServicePricing pricing,
                          NotificationService notificationService, AuditService auditService) {
        this.db = db;
        this.clock = clock;
        this.gateway = gateway;
        this.pricing = pricing;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> createPayment(long groomingId, String channelStr, String operator, Long ownerCustomerId) {
        var groomingRows = db.queryForList("select * from grooming where id=? for update", groomingId);
        if (groomingRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在: " + groomingId);
        }
        var order = groomingRows.get(0);
        long customerId = ((Number) order.get("customer_id")).longValue();
        if (ownerCustomerId != null && customerId != ownerCustomerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅支持本人办理工单自付结算");
        }

        String orderStatus = (String) order.get("status");
        if (!"WAITING_PICKUP".equals(orderStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单不在待接宠状态，无法发起自付结算");
        }

        var pendingAddons = db.queryForList("select id from addon where grooming_id=? and status='PENDING'", groomingId);
        if (!pendingAddons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先处理待确认的加项");
        }

        var existingSettlements = db.queryForList("select grooming_id, status from service_settlement where grooming_id=?", groomingId);
        if (!existingSettlements.isEmpty() && !"REFUNDED".equals(existingSettlements.get(0).get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该工单已经结算");
        }

        PaymentChannel channel = PaymentChannel.fromString(channelStr);
        if (channel == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择支付宝支付");
        }

        BigDecimal base;
        if (order.get("base_price") != null) {
            base = (BigDecimal) order.get("base_price");
        } else if (order.get("appointment_id") != null) {
            base = db.queryForObject("select price from appointment where id=?", BigDecimal.class, order.get("appointment_id"));
        } else {
            var service = db.queryForMap("select * from service where id=?", order.get("service_id"));
            String size = (String) db.queryForObject("select size_type from pet where id=?", String.class, order.get("pet_id"));
            base = pricing.quote(service, size).price();
        }
        BigDecimal addons = db.queryForObject("select coalesce(sum(amount),0) from addon where grooming_id=? and status='APPROVED'", BigDecimal.class, groomingId);
        if (addons == null) addons = BigDecimal.ZERO;
        BigDecimal totalAmount = base.add(addons);

        LocalDateTime now = LocalDateTime.now(clock);
        int timeoutMinutes = gateway.getProperties().getTimeoutMinutes();
        String paymentNo = "PAY" + now.format(DATE_NUM_FORMATTER) + (1000 + ThreadLocalRandom.current().nextInt(9000));

        PaymentChannelHandler handler = gateway.getHandler(channel);
        UnifiedOrderModel unifiedOrder = new UnifiedOrderModel(
            paymentNo,
            channel,
            totalAmount,
            "宠护洗护自付结算-工单#" + groomingId,
            String.valueOf(groomingId),
            timeoutMinutes
        );

        PaymentOrderResult orderResult = handler.createOrder(unifiedOrder);

        db.update(
            "insert into payment_order(payment_no, grooming_id, customer_id, channel, amount, status, trade_no, qr_code, expire_at, paid_at, created_at, biz_type, related_id) " +
            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            paymentNo, groomingId, customerId, channel.name(), totalAmount, PaymentStatus.PENDING.name(),
            null, orderResult.qrCode(), orderResult.expireAt(), null, now, "GROOMING", groomingId
        );

        Map<String, Object> petInfo = db.queryForMap("select name, breed from pet where id=?", order.get("pet_id"));
        Map<String, Object> serviceInfo = db.queryForMap("select name from service where id=?", order.get("service_id"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentNo", paymentNo);
        result.put("groomingId", groomingId);
        result.put("customerId", customerId);
        result.put("channel", channel.name());
        result.put("channelName", channel.getDescription());
        result.put("amount", totalAmount);
        result.put("baseAmount", base);
        result.put("addonAmount", addons);
        result.put("status", PaymentStatus.PENDING.name());
        result.put("qrCode", orderResult.qrCode());
        result.put("expireAt", orderResult.expireAt().format(STAMP_FORMATTER));
        result.put("petName", petInfo.get("name"));
        result.put("petBreed", petInfo.get("breed"));
        result.put("serviceName", serviceInfo.get("name"));
        result.put("pickupCode", order.get("pickup_code"));
        result.put("sandbox", gateway.getProperties().isSandboxEnabled());
        result.put("bizType", "GROOMING");
        result.put("subject", "洗护工单#" + groomingId);
        return result;
    }

    @Transactional
    public Map<String, Object> createRechargePayment(BigDecimal amount, String channelStr, String operator, long customerId) {
        if (amount == null || amount.scale() > 2 || amount.signum() <= 0 || amount.compareTo(new BigDecimal("9999999.99")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的充值金额，最多两位小数");
        }
        if (db.queryForList("select id from customer where id=?", customerId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会员不存在");
        }
        PaymentChannel channel = PaymentChannel.fromString(channelStr);
        if (channel == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择支付宝支付");
        LocalDateTime now = LocalDateTime.now(clock);
        int timeoutMinutes = gateway.getProperties().getTimeoutMinutes();
        String paymentNo = "PAY" + now.format(DATE_NUM_FORMATTER) + (1000 + ThreadLocalRandom.current().nextInt(9000));
        PaymentChannelHandler handler = gateway.getHandler(channel);
        UnifiedOrderModel unifiedOrder = new UnifiedOrderModel(paymentNo, channel, amount, "会员储值充值", "RECHARGE-" + customerId, timeoutMinutes);
        PaymentOrderResult orderResult = handler.createOrder(unifiedOrder);
        db.update(
            "insert into payment_order(payment_no, grooming_id, customer_id, channel, amount, status, trade_no, qr_code, expire_at, paid_at, created_at, biz_type, related_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            paymentNo, null, customerId, channel.name(), amount, PaymentStatus.PENDING.name(),
            null, orderResult.qrCode(), orderResult.expireAt(), null, now, "RECHARGE", customerId
        );
        return paymentView(paymentNo, customerId);
    }

    @Transactional
    public Map<String, Object> createRetailPayment(long orderId, String channelStr, String operator, Long ownerCustomerId) {
        var orders = db.queryForList("select * from retail_order where id=? for update", orderId);
        if (orders.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品订单不存在");
        var order = orders.get(0);
        long customerId = ((Number) order.get("customer_id")).longValue();
        if (ownerCustomerId != null && customerId != ownerCustomerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅支持支付本人的商品订单");
        }
        if (!"SUBMITTED".equals(order.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单不在待收款状态");
        }
        PaymentChannel channel = PaymentChannel.fromString(channelStr);
        if (channel == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择支付宝支付");
        BigDecimal amount = (BigDecimal) order.get("amount");
        LocalDateTime now = LocalDateTime.now(clock);
        int timeoutMinutes = gateway.getProperties().getTimeoutMinutes();
        String paymentNo = "PAY" + now.format(DATE_NUM_FORMATTER) + (1000 + ThreadLocalRandom.current().nextInt(9000));
        PaymentChannelHandler handler = gateway.getHandler(channel);
        UnifiedOrderModel unifiedOrder = new UnifiedOrderModel(paymentNo, channel, amount, "洗护用品订单#" + orderId, String.valueOf(orderId), timeoutMinutes);
        PaymentOrderResult orderResult = handler.createOrder(unifiedOrder);
        db.update(
            "insert into payment_order(payment_no, grooming_id, customer_id, channel, amount, status, trade_no, qr_code, expire_at, paid_at, created_at, biz_type, related_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            paymentNo, null, customerId, channel.name(), amount, PaymentStatus.PENDING.name(),
            null, orderResult.qrCode(), orderResult.expireAt(), null, now, "RETAIL", orderId
        );
        return paymentView(paymentNo, ownerCustomerId);
    }

    public Map<String, Object> getPaymentStatus(String paymentNo, Long ownerCustomerId) {
        var payments = db.queryForList("select * from payment_order where payment_no=?", paymentNo);
        if (payments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到支付记录: " + paymentNo);
        }
        var payment = payments.get(0);
        long customerId = ((Number) payment.get("customer_id")).longValue();
        if (ownerCustomerId != null && customerId != ownerCustomerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅能查询本人的支付记录");
        }

        String status = (String) payment.get("status");
        java.sql.Timestamp expireTimestamp = (java.sql.Timestamp) payment.get("expire_at");
        LocalDateTime expireAt = expireTimestamp != null ? expireTimestamp.toLocalDateTime() : null;
        LocalDateTime now = LocalDateTime.now(clock);

        if (PaymentStatus.PENDING.name().equals(status) && expireAt != null && expireAt.isBefore(now)) {
            db.update("update payment_order set status=? where payment_no=?", PaymentStatus.CLOSED.name(), paymentNo);
            status = PaymentStatus.CLOSED.name();
            payment.put("status", status);
        } else if (PaymentStatus.PENDING.name().equals(status)) {
            PaymentChannel ch = PaymentChannel.fromString((String) payment.get("channel"));
            if (ch != null) {
                PaymentChannelHandler handler = gateway.getHandler(ch);
                if (handler != null && handler.isLiveConfigured()) {
                    var q = handler.queryOrder(paymentNo, (String) payment.get("trade_no"));
                    if (q != null && q.status() == PaymentStatus.SUCCESS) {
                        completePayment(paymentNo, q.tradeNo(), "QUERY_SYNC");
                        status = PaymentStatus.SUCCESS.name();
                        payment.put("status", status);
                    }
                }
            }
        }

        long groomingId = payment.get("grooming_id") instanceof Number n ? n.longValue() : 0;
        String bizType = payment.get("biz_type") == null ? "GROOMING" : payment.get("biz_type").toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentNo", paymentNo);
        result.put("groomingId", groomingId == 0 ? null : groomingId);
        result.put("orderId", "RETAIL".equals(bizType) && payment.get("related_id") instanceof Number related ? related.longValue() : null);
        result.put("customerId", customerId);
        result.put("channel", payment.get("channel"));
        PaymentChannel chName = PaymentChannel.fromString((String) payment.get("channel"));
        result.put("channelName", chName != null ? chName.getDescription() : payment.get("channel"));
        result.put("amount", payment.get("amount"));
        result.put("status", status);
        result.put("tradeNo", payment.get("trade_no"));
        result.put("qrCode", payment.get("qr_code"));
        result.put("expireAt", expireAt != null ? expireAt.format(STAMP_FORMATTER) : null);
        java.sql.Timestamp paidTimestamp = (java.sql.Timestamp) payment.get("paid_at");
        result.put("paidAt", paidTimestamp != null ? paidTimestamp.toLocalDateTime().format(STAMP_FORMATTER) : null);
        result.put("sandbox", gateway.getProperties().isSandboxEnabled());
        result.put("bizType", bizType);
        if (groomingId > 0) {
            var grooming = db.queryForMap("select * from grooming where id=?", groomingId);
            Map<String, Object> pet = db.queryForMap("select name, breed from pet where id=?", grooming.get("pet_id"));
            Map<String, Object> service = db.queryForMap("select name from service where id=?", grooming.get("service_id"));
            result.put("petName", pet.get("name"));
            result.put("petBreed", pet.get("breed"));
            result.put("serviceName", service.get("name"));
            result.put("pickupCode", grooming.get("pickup_code"));
            result.put("subject", "洗护工单#" + groomingId);
        } else if ("RECHARGE".equals(bizType)) {
            result.put("subject", "会员储值充值");
        } else if ("RETAIL".equals(bizType)) {
            result.put("subject", "商品订单#" + payment.get("related_id"));
        }
        return result;
    }

    private Map<String, Object> paymentView(String paymentNo, Long ownerCustomerId) {
        return getPaymentStatus(paymentNo, ownerCustomerId);
    }

    @Transactional
    public void completePayment(String paymentNo, String tradeNo, String operator) {
        var payments = db.queryForList("select * from payment_order where payment_no=? for update", paymentNo);
        if (payments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到支付记录: " + paymentNo);
        }
        var payment = payments.get(0);
        String currentStatus = (String) payment.get("status");
        if (PaymentStatus.SUCCESS.name().equals(currentStatus)) {
            return;
        }
        if (PaymentStatus.CLOSED.name().equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "支付订单已超时关闭");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        db.update("update payment_order set status=?, trade_no=?, paid_at=? where payment_no=?",
            PaymentStatus.SUCCESS.name(), tradeNo, now, paymentNo);

        String bizType = payment.get("biz_type") == null ? "GROOMING" : payment.get("biz_type").toString();
        if ("RECHARGE".equals(bizType)) {
            completeRecharge(payment, operator, now, tradeNo);
            return;
        }
        if ("RETAIL".equals(bizType)) {
            completeRetail(payment, operator, now, tradeNo);
            return;
        }

        long groomingId = ((Number) payment.get("grooming_id")).longValue();
        long customerId = ((Number) payment.get("customer_id")).longValue();
        String channel = (String) payment.get("channel");
        BigDecimal amount = (BigDecimal) payment.get("amount");

        var groomingOrder = db.queryForMap("select * from grooming where id=?", groomingId);
        BigDecimal base = groomingOrder.get("base_price") != null ? (BigDecimal) groomingOrder.get("base_price") : BigDecimal.ZERO;
        BigDecimal addons = db.queryForObject("select coalesce(sum(amount),0) from addon where grooming_id=? and status='APPROVED'", BigDecimal.class, groomingId);
        if (addons == null) addons = BigDecimal.ZERO;
        if (base.signum() == 0) {
            base = amount.subtract(addons);
        }

        String op = (operator != null && !operator.isBlank()) ? operator : "ONLINE_PAY";

        var existingSettlements = db.queryForList("select grooming_id, status from service_settlement where grooming_id=?", groomingId);
        if (existingSettlements.isEmpty()) {
            db.update("insert into service_settlement(grooming_id, customer_id, base_amount, addon_amount, amount, method, card_id, status, operator_name, paid_at, trade_no) values (?,?,?,?,?,?,?,?,?,?,?)",
                groomingId, customerId, base, addons, amount, channel, null, "PAID", op, now, tradeNo);
        } else {
            db.update("update service_settlement set base_amount=?, addon_amount=?, amount=?, method=?, card_id=NULL, status='PAID', operator_name=?, paid_at=?, refunded_at=NULL, refund_reason=NULL, trade_no=? where grooming_id=?",
                base, addons, amount, channel, op, now, tradeNo, groomingId);
        }

        notificationService.createNotification("OWNER", customerId, "PAYMENT_SUCCESS", "自付结算成功", "洗护工单 #" + groomingId + " 支付已完成，金额：￥" + amount, paymentNo, false);
        auditService.log("PAYMENT_COMPLETE", op, "GROOMING", String.valueOf(groomingId), "自付结算完成，渠道: " + channel + "，金额: " + amount + "，流水号: " + tradeNo);
    }

    private void completeRecharge(Map<String, Object> payment, String operator, LocalDateTime now, String tradeNo) {
        long customerId = ((Number) payment.get("customer_id")).longValue();
        BigDecimal amount = (BigDecimal) payment.get("amount");
        String paymentNo = payment.get("payment_no").toString();
        String op = (operator != null && !operator.isBlank()) ? operator : "ONLINE_PAY";
        db.update("update customer set balance=balance+? where id=?", amount, customerId);
        db.update("insert into balance_flow(customer_id,grooming_id,amount,operator_name,created_at) values (?,NULL,?,?,?)", customerId, amount, op, now);
        var existing = db.queryForList("select request_id from member_recharge where request_id=?", paymentNo);
        if (existing.isEmpty()) {
            db.update("insert into member_recharge(request_id,customer_id,amount,operator_name,created_at) values (?,?,?,?,?)", paymentNo, customerId, amount, op, now);
        }
        notificationService.createNotification("OWNER", customerId, "RECHARGE_SUCCESS", "会员充值成功", "您的会员账户已成功充值 ￥" + amount, paymentNo, false);
        auditService.log("MEMBER_RECHARGE", op, "CUSTOMER", String.valueOf(customerId), "支付宝充值，金额: ￥" + amount + "，流水号: " + tradeNo);
    }

    private void completeRetail(Map<String, Object> payment, String operator, LocalDateTime now, String tradeNo) {
        if (!(payment.get("related_id") instanceof Number related)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "支付单未绑定商品订单");
        }
        long orderId = related.longValue();
        BigDecimal amount = (BigDecimal) payment.get("amount");
        String op = (operator != null && !operator.isBlank()) ? operator : "ONLINE_PAY";
        var orders = db.queryForList("select * from retail_order where id=? for update", orderId);
        if (orders.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品订单不存在");
        var order = orders.get(0);
        if ("SUBMITTED".equals(order.get("status"))) {
            db.update("update retail_order set status='PAID', pay_method=? where id=?", "ALIPAY", orderId);
            db.update("insert into retail_order_event(order_id,action,amount,reason,operator_name,created_at) values (?,?,?,?,?,?)",
                orderId, "PAID", amount, "支付宝在线支付", op, now);
        }
        auditService.log("ORDER_SETTLE", op, "ORDER", String.valueOf(orderId), "商品订单支付宝结算，金额: " + amount + "，流水号: " + tradeNo);
        notificationService.createNotification("OWNER", ((Number) order.get("customer_id")).longValue(), "PAYMENT_SUCCESS", "商品订单已支付", "订单 #" + orderId + " 已完成支付宝支付，金额：￥" + amount, String.valueOf(orderId), false);
    }

    @Transactional
    public Map<String, Object> mockPay(String paymentNo, String operator, Long ownerCustomerId) {
        if (!gateway.getProperties().isSandboxEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "沙箱模拟支付功能已关闭，请使用真实支付渠道");
        }

        var payments = db.queryForList("select * from payment_order where payment_no=?", paymentNo);
        if (payments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到支付记录: " + paymentNo);
        }
        var payment = payments.get(0);
        long customerId = ((Number) payment.get("customer_id")).longValue();
        if (ownerCustomerId != null && customerId != ownerCustomerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅能操作本人的支付单");
        }

        String status = (String) payment.get("status");
        if (PaymentStatus.CLOSED.name().equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "支付单已关闭，请重新发起");
        }
        if (PaymentStatus.SUCCESS.name().equals(status)) {
            return getPaymentStatus(paymentNo, ownerCustomerId);
        }

        String channel = (String) payment.get("channel");
        String mockTradeNo = "SIM_" + channel + "_" + System.currentTimeMillis();
        completePayment(paymentNo, mockTradeNo, operator);
        return getPaymentStatus(paymentNo, ownerCustomerId);
    }

    public PaymentNotifyResult handleNotify(String channelStr, String body, Map<String, String> headers, Map<String, String[]> params) {
        PaymentChannel channel = PaymentChannel.fromString(channelStr);
        if (channel == null) {
            return PaymentNotifyResult.fail("未知支付渠道: " + channelStr, "fail", "text/plain");
        }
        PaymentChannelHandler handler = gateway.getHandler(channel);
        PaymentNotifyResult notifyResult = handler.parseAndVerifyNotify(body, headers, params);
        if (notifyResult.success()) {
            var orders = db.queryForList("select amount, status from payment_order where payment_no=?", notifyResult.paymentNo());
            if (orders.isEmpty()) {
                return PaymentNotifyResult.fail("支付单不存在: " + notifyResult.paymentNo(), "fail", "text/plain");
            }
            if (handler.isLiveConfigured() && notifyResult.amount() != null) {
                BigDecimal orderAmount = (BigDecimal) orders.get(0).get("amount");
                if (orderAmount.compareTo(notifyResult.amount()) != 0) {
                    return PaymentNotifyResult.fail("支付金额不匹配: 期望 " + orderAmount + ", 实际 " + notifyResult.amount(), "fail", "text/plain");
                }
            }
            completePayment(notifyResult.paymentNo(), notifyResult.tradeNo(), "NOTIFY_" + channel.name());
        }
        return notifyResult;
    }
}
