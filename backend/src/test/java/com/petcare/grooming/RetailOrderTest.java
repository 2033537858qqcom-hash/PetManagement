package com.petcare.grooming;

import com.petcare.grooming.care.LifecycleScheduler;
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

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:retail;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc @Transactional
class RetailOrderTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Test void retriesDoNotDuplicateOrdersAndRefundRestoresOriginalStockUnits() throws Exception {
        db.update("update product set sale_units=500 where id=1");
        String body="{\"productId\":1,\"quantity\":1,\"requestId\":\"9fb82d51-68c4-4adb-89a8-065b592a1d88\"}";
        String created=mvc.perform(post("/api/orders").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int id=JsonPath.read(created,"$.data.id");
        mvc.perform(post("/api/orders").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        assertEquals(1800,db.queryForObject("select stock from product where id=1",Integer.class));
        mvc.perform(post("/api/orders/"+id+"/settle").with(user("reception").roles("RECEPTION")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));
        mvc.perform(post("/api/orders/"+id+"/settle").with(user("reception").roles("RECEPTION")).with(csrf())).andExpect(status().isConflict());
        db.update("update product set sale_units=100 where id=1");
        mvc.perform(post("/api/orders/"+id+"/refund").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content("{\"reason\":\"退货\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REFUNDED"));
        mvc.perform(post("/api/orders/"+id+"/refund").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content("{\"reason\":\"退货\"}"))
            .andExpect(status().isConflict());
        assertEquals(2300,db.queryForObject("select stock from product where id=1",Integer.class));
    }
    @Autowired LifecycleScheduler scheduler;
    @Test void staffPlacesOrderSettlesWithBalanceAndSchedulerReleasesExpiredOrders() throws Exception {
        java.math.BigDecimal initialBalance=db.queryForObject("select balance from customer where id=1",java.math.BigDecimal.class);
        String created=mvc.perform(post("/api/orders").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"customerId\":1,\"productId\":1,\"quantity\":1,\"requestId\":\"a1111111-68c4-4adb-89a8-065b592a1d88\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int id=JsonPath.read(created,"$.data.id");
        mvc.perform(post("/api/orders/"+id+"/settle").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content("{\"method\":\"BALANCE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));
        assertEquals("BALANCE", db.queryForObject("select pay_method from retail_order where id=?", String.class, id));
        mvc.perform(get("/api/orders").with(user("reception").roles("RECEPTION")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].customer_name").value("林知夏"))
            .andExpect(jsonPath("$.data[0].phone").value("13800138001"));
        assertEquals(0,initialBalance.subtract(new java.math.BigDecimal("68.00")).compareTo(db.queryForObject("select balance from customer where id=1",java.math.BigDecimal.class)));
        mvc.perform(post("/api/orders/"+id+"/refund").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content("{\"reason\":\"会员退货\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REFUNDED"));
        assertEquals(0,initialBalance.compareTo(db.queryForObject("select balance from customer where id=1",java.math.BigDecimal.class)));

        String expiredOrder=mvc.perform(post("/api/orders").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json")
            .content("{\"productId\":1,\"quantity\":1,\"requestId\":\"b2222222-68c4-4adb-89a8-065b592a1d88\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int expId=JsonPath.read(expiredOrder,"$.data.id");
        db.update("update retail_order set created_at=DATEADD('MINUTE', -45, CURRENT_TIMESTAMP) where id=?", expId);
        org.junit.jupiter.api.Assertions.assertTrue(scheduler.releaseExpiredUnpaidOrders() >= 1);
        assertEquals("CANCELLED", db.queryForObject("select status from retail_order where id=?", String.class, expId));

        db.update("update appointment set appointment_date=DATEADD('DAY', -1, CURRENT_DATE) where id=3");
        org.junit.jupiter.api.Assertions.assertTrue(scheduler.markOverdueNoShows() >= 1);
        assertEquals("NO_SHOW", db.queryForObject("select status from appointment where id=3", String.class));
    }
}
