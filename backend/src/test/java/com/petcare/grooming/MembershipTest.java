package com.petcare.grooming;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:membership;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc @Transactional
class MembershipTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Test void rechargeIsIdempotentAndRestricted() throws Exception {
        String body="{\"amount\":100,\"requestId\":\"6f12bc93-dae1-4015-a3f2-14df4376aa12\"}";
        mvc.perform(post("/api/customers/1/recharge").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/customers/1/recharge").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/customers/1/recharge").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        assertEquals(0,new java.math.BigDecimal("780.00").compareTo(db.queryForObject("select balance from customer where id=1",java.math.BigDecimal.class)));
        assertEquals(1,db.queryForObject("select count(*) from member_recharge where customer_id=1",Integer.class));
        assertEquals(1,db.queryForObject("select count(*) from audit_log where action_type='MEMBER_RECHARGE' and target_id='1'",Integer.class));
        assertEquals(1,db.queryForObject("select count(*) from notification where category='RECHARGE_SUCCESS' and customer_id=1",Integer.class));
        mvc.perform(post("/api/customers/2/recharge").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
    }
    @Test void cardSaleRecordsPaymentAndAllowedServices() throws Exception {
        String body="{\"name\":\"基础卡\",\"total\":5,\"price\":400,\"serviceIds\":[1,2],\"requestId\":\"2671d74e-4fd0-4e23-ae96-0b3c3b70f0d7\"}";
        String response=mvc.perform(post("/api/customers/1/cards").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.remaining").value(5)).andReturn().getResponse().getContentAsString();
        int id=JsonPath.read(response,"$.data.id");
        mvc.perform(post("/api/customers/1/cards").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        assertEquals(2,db.queryForObject("select count(*) from prepaid_card_service where card_id=?",Integer.class,id));
        assertEquals(1,db.queryForObject("select count(*) from prepaid_card_sale where card_id=?",Integer.class,id));
        assertEquals(1,db.queryForObject("select count(*) from audit_log where action_type='CARD_SALE' and target_id='1'",Integer.class));
        assertEquals(1,db.queryForObject("select count(*) from notification where category='CARD_PURCHASE' and customer_id=1",Integer.class));
    }
}
