package com.petcare.grooming.payment;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.common.ApiResponse;
import com.petcare.grooming.payment.model.PaymentNotifyResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {
    private final PaymentService paymentService;
    private final AccountAccess access;

    public PaymentController(PaymentService paymentService, AccountAccess access) {
        this.paymentService = paymentService;
        this.access = access;
    }

    public record PaymentCreateForm(Long groomingId, Long orderId, BigDecimal amount, String channel) {}

    @PostMapping("/create")
    public ApiResponse<?> createPayment(@RequestBody PaymentCreateForm form) {
        if (form.channel() == null || form.channel().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择支付渠道 (ALIPAY)");
        }

        Long ownerCustomerId = null;
        if (access.role("OWNER")) {
            ownerCustomerId = access.customerId();
        } else if (!(access.role("MANAGER") || access.role("ADMIN") || access.role("RECEPTION"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权办理支付结算");
        }

        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        if (form.groomingId() != null) {
            return ApiResponse.ok(paymentService.createPayment(form.groomingId(), form.channel(), operator, ownerCustomerId));
        }
        if (form.orderId() != null) {
            return ApiResponse.ok(paymentService.createRetailPayment(form.orderId(), form.channel(), operator, ownerCustomerId));
        }
        if (form.amount() != null) {
            if (ownerCustomerId == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "会员自助充值仅支持宠主本人使用支付宝");
            }
            return ApiResponse.ok(paymentService.createRechargePayment(form.amount(), form.channel(), operator, ownerCustomerId));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请指定待结算工单、商品订单或充值金额");
    }

    @GetMapping("/{paymentNo}")
    public ApiResponse<?> getPaymentStatus(@PathVariable String paymentNo) {
        Long ownerCustomerId = null;
        if (access.role("OWNER")) {
            ownerCustomerId = access.customerId();
        } else if (!(access.role("MANAGER") || access.role("ADMIN") || access.role("RECEPTION"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查询支付记录");
        }

        Map<String, Object> result = paymentService.getPaymentStatus(paymentNo, ownerCustomerId);
        return ApiResponse.ok(result);
    }

    @PostMapping("/{paymentNo}/mock-pay")
    public ApiResponse<?> mockPay(@PathVariable String paymentNo) {
        Long ownerCustomerId = null;
        if (access.role("OWNER")) {
            ownerCustomerId = access.customerId();
        } else if (!(access.role("MANAGER") || access.role("ADMIN") || access.role("RECEPTION"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权进行模拟支付操作");
        }

        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        Map<String, Object> result = paymentService.mockPay(paymentNo, operator, ownerCustomerId);
        return ApiResponse.ok(result);
    }

    @PostMapping("/notify/{channel}")
    public ResponseEntity<String> notify(
            @PathVariable String channel,
            @RequestBody(required = false) String body,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        PaymentNotifyResult result = paymentService.handleNotify(channel, body, headers, params);

        HttpHeaders responseHeaders = new HttpHeaders();
        if (result.contentType() != null && result.contentType().contains("json")) {
            responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        } else {
            responseHeaders.setContentType(MediaType.TEXT_PLAIN);
        }

        return new ResponseEntity<>(result.responseContent(), responseHeaders, HttpStatus.OK);
    }
}
