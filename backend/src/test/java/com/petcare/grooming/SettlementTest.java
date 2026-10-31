package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:settlement;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc @Transactional
class SettlementTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Test void cardMustMatchCustomerAndServiceAndRefundOnlyOnce() throws Exception {
        mvc.perform(post("/api/grooming/2/settle").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content("{\"method\":\"CARD\",\"cardId\":1}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/grooming/2/settle").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content("{\"method\":\"CARD\",\"cardId\":2}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/grooming/2/settle").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content("{\"method\":\"CARD\",\"cardId\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));
        assertEquals(5, db.queryForObject("select remaining from prepaid_card where id=1", Integer.class));
        mvc.perform(post("/api/grooming/2/refund").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content("{\"reason\":\"测试退回\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REFUNDED"));
        mvc.perform(post("/api/grooming/2/refund").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content("{\"reason\":\"测试退回\"}"))
            .andExpect(status().isConflict());
        assertEquals(6, db.queryForObject("select remaining from prepaid_card where id=1", Integer.class));
    }

    @Test void comboCardAndAddonSettlementDeductsCardAndBalanceAndRefundsBoth() throws Exception {
        db.update("insert into addon(grooming_id,name,reason,amount,status,request_id) values (2,'去打结','毛发打结',40.00,'APPROVED','c133f767-352e-4478-8db1-07910834b3ec')");
        java.math.BigDecimal initialBalance = db.queryForObject("select balance from customer where id=1", java.math.BigDecimal.class);
        mvc.perform(post("/api/grooming/2/settle").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"method\":\"CARD\",\"cardId\":1,\"addonMethod\":\"BALANCE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PAID"))
            .andExpect(jsonPath("$.data.addon_amount").value(40.0));
        assertEquals(5, db.queryForObject("select remaining from prepaid_card where id=1", Integer.class));
        assertEquals(0, initialBalance.subtract(new java.math.BigDecimal("40.00")).compareTo(db.queryForObject("select balance from customer where id=1", java.math.BigDecimal.class)));
        mvc.perform(post("/api/grooming/2/refund").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content("{\"reason\":\"组合退款测试\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REFUNDED"));
        assertEquals(6, db.queryForObject("select remaining from prepaid_card where id=1", Integer.class));
        assertEquals(0, initialBalance.compareTo(db.queryForObject("select balance from customer where id=1", java.math.BigDecimal.class)));
    }

    @Test void comboCardAndBalanceAddonRejectsAndRollsBackCardWhenBalanceInsufficient() throws Exception {
        db.update("insert into addon(grooming_id,name,reason,amount,status,request_id) values (2,'豪华全身药浴','皮肤深层护理',900.00,'APPROVED','d244f767-352e-4478-8db1-07910834b3ec')");
        mvc.perform(post("/api/grooming/2/settle").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"method\":\"CARD\",\"cardId\":1,\"addonMethod\":\"BALANCE\"}"))
            .andExpect(status().isConflict());
        assertEquals(6, db.queryForObject("select remaining from prepaid_card where id=1", Integer.class));
    }

    @Test void refundedGroomingAllowsHandover() throws Exception {
        mvc.perform(post("/api/grooming/2/settle").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content("{\"method\":\"CARD\",\"cardId\":1}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/grooming/2/refund").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content("{\"reason\":\"客户满意度退款\"}"))
            .andExpect(status().isOk());
        String pickupCode = db.queryForObject("select pickup_code from grooming where id=2", String.class);
        mvc.perform(post("/api/grooming/2/handover").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content("{\"pickupCode\":\"" + pickupCode + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertEquals("COMPLETED", db.queryForObject("select status from grooming where id=2", String.class));
    }

    @Test void ownerSettleWithSufficientBalanceSucceeds() throws Exception {
        db.update("update customer set balance=500 where id=1");
        mvc.perform(post("/api/grooming/2/settle").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content("{\"method\":\"BALANCE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PAID"));
        assertEquals(1, db.queryForObject("select count(*) from balance_flow where grooming_id=2 and customer_id=1", Integer.class));
    }

    @Test void ownerSettleWithInsufficientBalanceRejectsWithConflict() throws Exception {
        db.update("update customer set balance=0 where id=1");
        mvc.perform(post("/api/grooming/2/settle").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content("{\"method\":\"BALANCE\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("会员余额不足")));
    }
}
