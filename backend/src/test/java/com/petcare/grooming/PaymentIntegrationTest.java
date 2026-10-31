package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
@Transactional
class PaymentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @Test
    void ownerCanInitiateAlipayPaymentAndReceiveQrCode() throws Exception {
        mvc.perform(post("/api/payment/create")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"groomingId\":2,\"channel\":\"ALIPAY\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.channel").value("ALIPAY"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.qrCode", startsWith("https://qr.alipay.com/")))
            .andExpect(jsonPath("$.data.paymentNo", startsWith("PAY")))
            .andExpect(jsonPath("$.data.petName").value("糯米"));

        Integer count = db.queryForObject("select count(*) from payment_order where grooming_id=2 and channel='ALIPAY'", Integer.class);
        assertEquals(1, count);
    }

    @Test
    void cannotInitiatePaymentForGroomingNotInWaitingPickup() throws Exception {
        mvc.perform(post("/api/payment/create")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"groomingId\":4,\"channel\":\"ALIPAY\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("工单不在待接宠状态")));
    }

    @Test
    void cannotInitiatePaymentWithInvalidChannel() throws Exception {
        mvc.perform(post("/api/payment/create")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"groomingId\":2,\"channel\":\"WECHAT\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("请选择支付宝支付")));
    }

    @Test
    void anotherOwnerCannotPayForDifferentCustomerGrooming() throws Exception {
        db.update("insert into account(username,customer_id,employee_id,role_code,display_name,title) values ('owner2',2,NULL,'OWNER','陈思远','银卡会员')");
        mvc.perform(post("/api/payment/create")
                .with(user("owner2").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"groomingId\":2,\"channel\":\"ALIPAY\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void queryPaymentStatusAndMockPaySettlesGroomingOrder() throws Exception {
        String resp = mvc.perform(post("/api/payment/create")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"groomingId\":2,\"channel\":\"ALIPAY\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String paymentNo = com.jayway.jsonpath.JsonPath.read(resp, "$.data.paymentNo");
        assertNotNull(paymentNo);

        mvc.perform(get("/api/payment/" + paymentNo)
                .with(user("owner").roles("OWNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));

        mvc.perform(post("/api/payment/" + paymentNo + "/mock-pay")
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.tradeNo", startsWith("SIM_ALIPAY_")));

        mvc.perform(get("/api/payment/" + paymentNo)
                .with(user("owner").roles("OWNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        String settlementStatus = db.queryForObject("select status from service_settlement where grooming_id=2", String.class);
        String settlementMethod = db.queryForObject("select method from service_settlement where grooming_id=2", String.class);
        String tradeNo = db.queryForObject("select trade_no from service_settlement where grooming_id=2", String.class);
        assertEquals("PAID", settlementStatus);
        assertEquals("ALIPAY", settlementMethod);
        assertNotNull(tradeNo);

        mvc.perform(post("/api/payment/create")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"groomingId\":2,\"channel\":\"ALIPAY\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("已经结算")));
    }

    @Test
    void alipayNotifyWebhookCompletesPayment() throws Exception {
        String resp = mvc.perform(post("/api/payment/create")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"groomingId\":2,\"channel\":\"ALIPAY\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String paymentNo = com.jayway.jsonpath.JsonPath.read(resp, "$.data.paymentNo");

        mvc.perform(post("/api/payment/notify/alipay")
                .param("out_trade_no", paymentNo)
                .param("trade_no", "ALI_TX_9876543210")
                .param("total_amount", "148.00")
                .param("trade_status", "TRADE_SUCCESS"))
            .andExpect(status().isOk())
            .andExpect(content().string("success"));

        String settlementStatus = db.queryForObject("select status from service_settlement where grooming_id=2", String.class);
        String settlementMethod = db.queryForObject("select method from service_settlement where grooming_id=2", String.class);
        assertEquals("PAID", settlementStatus);
        assertEquals("ALIPAY", settlementMethod);
    }

    @Test
    void expiredPaymentOrderIsClosedOnQuery() throws Exception {
        String paymentNo = "PAY_EXPIRED_TEST_01";
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        db.update("insert into payment_order(payment_no, grooming_id, customer_id, channel, amount, status, expire_at, created_at) " +
                "values (?, 2, 1, 'ALIPAY', 148.00, 'PENDING', ?, ?)",
            paymentNo, past, past.minusMinutes(15));

        mvc.perform(get("/api/payment/" + paymentNo)
                .with(user("owner").roles("OWNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CLOSED"));

        assertEquals("CLOSED", db.queryForObject("select status from payment_order where payment_no=?", String.class, paymentNo));

        mvc.perform(post("/api/payment/" + paymentNo + "/mock-pay")
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("已关闭")));
    }

    @Test
    void ownerCanPayRetailOrderWithAlipay() throws Exception {
        String resp = mvc.perform(post("/api/payment/create")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"orderId\":1,\"channel\":\"ALIPAY\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bizType").value("RETAIL"))
            .andReturn().getResponse().getContentAsString();
        String paymentNo = com.jayway.jsonpath.JsonPath.read(resp, "$.data.paymentNo");
        mvc.perform(post("/api/payment/" + paymentNo + "/mock-pay")
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCESS"));
        assertEquals("PAID", db.queryForObject("select status from retail_order where id=1", String.class));
        assertEquals("ALIPAY", db.queryForObject("select pay_method from retail_order where id=1", String.class));
    }

    @Test
    void ownerCanRechargeWithAlipay() throws Exception {
        String resp = mvc.perform(post("/api/payment/create")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"amount\":50,\"channel\":\"ALIPAY\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bizType").value("RECHARGE"))
            .andReturn().getResponse().getContentAsString();
        String paymentNo = com.jayway.jsonpath.JsonPath.read(resp, "$.data.paymentNo");
        mvc.perform(post("/api/payment/" + paymentNo + "/mock-pay")
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCESS"));
        assertEquals(0, new java.math.BigDecimal("730.00").compareTo(db.queryForObject("select balance from customer where id=1", java.math.BigDecimal.class)));
    }

    @Test
    void paymentCompleteWritesCorrectAuditLogParameters() throws Exception {
        String resp = mvc.perform(post("/api/payment/create")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"groomingId\":2,\"channel\":\"ALIPAY\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String paymentNo = com.jayway.jsonpath.JsonPath.read(resp, "$.data.paymentNo");
        mvc.perform(post("/api/payment/" + paymentNo + "/mock-pay")
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
            .andExpect(status().isOk());

        var auditLogs = db.queryForList("select * from audit_log where action_type='PAYMENT_COMPLETE' and target_id='2'");
        assertEquals(1, auditLogs.size());
        assertEquals("owner", auditLogs.get(0).get("operator_name"));
        assertEquals("GROOMING", auditLogs.get(0).get("target_type"));
    }
}
