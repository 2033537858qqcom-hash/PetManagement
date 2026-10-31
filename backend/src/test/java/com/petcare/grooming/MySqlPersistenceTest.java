package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named="mysql.integration", matches="true")
@ActiveProfiles({"mysql","mysql-demo"})
@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://127.0.0.1:13318/codex_pet_test_b03?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false", "spring.datasource.username=root", "spring.datasource.password=", "pet-grooming.demo-seed-enabled=true"})
@AutoConfigureMockMvc
class MySqlPersistenceTest {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;
    @Test void migratesAndSupportsRealLoginAndBusinessQueries() throws Exception {
        assertTrue(db.queryForObject("select count(*) from flyway_schema_history where success=1", Integer.class) > 0);
        assertTrue(db.queryForObject("select count(*) from pet", Integer.class) > 0);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"username\":\"owner\",\"password\":\"petcare123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.customerId").value(1));
    }
    @Test @Transactional void checkInWorksWithRealMySqlGeneratedKeys() throws Exception {
        db.update("update appointment set appointment_date=CURRENT_DATE where id=4");
        mvc.perform(post("/api/appointments/4/check-in").with(user("reception").roles("RECEPTION")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.appointment_id").value(4))
            .andExpect(jsonPath("$.data.stage").value("CHECK_IN"));
    }
    @Test @Transactional void membershipInventoryAndRefundWorkWithMySql() throws Exception {
        mvc.perform(post("/api/customers/1/recharge").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"amount\":100,\"requestId\":\"6379dc47-1a2f-485a-8799-bbc7f0d9b910\"}"))
            .andExpect(status().isOk());
        String card=mvc.perform(post("/api/customers/1/cards").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"name\":\"护理卡\",\"total\":5,\"price\":400,\"serviceIds\":[1],\"requestId\":\"a8993053-06fd-44b6-b6d1-c89248c5da25\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int cardId=com.jayway.jsonpath.JsonPath.read(card,"$.data.id");
        db.update("update grooming set stage='TRIMMING' where id=4");
        mvc.perform(post("/api/grooming/4/assessment").with(user("groomer").roles("GROOMER")).with(csrf()).contentType("application/json")
            .content("{\"weight\":5.2,\"skin\":\"CLEAR\",\"coat\":\"SMOOTH\",\"ear\":\"CLEAN\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/grooming/4/settle").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"method\":\"CARD\",\"cardId\":"+cardId+"}")).andExpect(status().isOk());
        assertEquals(4,db.queryForObject("select remaining from prepaid_card where id=?",Integer.class,cardId));
        mvc.perform(post("/api/grooming/4/refund").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content("{\"reason\":\"测试退款\"}"))
            .andExpect(status().isOk());
        assertEquals(5,db.queryForObject("select remaining from prepaid_card where id=?",Integer.class,cardId));
        mvc.perform(post("/api/inventory/1/adjust").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"kind\":\"IN\",\"quantity\":20,\"reason\":\"补货\",\"requestId\":\"ab68767d-92c1-4fd9-8dfa-b150099ae340\"}"))
            .andExpect(status().isOk());
        String order=mvc.perform(post("/api/orders").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json")
            .content("{\"productId\":1,\"quantity\":1,\"requestId\":\"3d6aa945-f804-45fa-8863-c337d7e1ac79\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int orderId=com.jayway.jsonpath.JsonPath.read(order,"$.data.id");
        mvc.perform(post("/api/orders/"+orderId+"/cancel").with(user("owner").roles("OWNER")).with(csrf())).andExpect(status().isOk());
        assertEquals(2,db.queryForObject("select count(*) from inventory_flow where product_id=1 and reference_id=? and action in ('SALE','CANCEL')",Integer.class,orderId));
    }
    @Test void simultaneousPurchasesCannotOversell() throws Exception {
        int originalStock = db.queryForObject("select stock from product where id=1", Integer.class);
        long originalLastOrder = db.queryForObject("select coalesce(max(id),0) from retail_order", Long.class);
        db.update("update product set stock=1 where id=1");
        var pool = Executors.newFixedThreadPool(2);
        try (var lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            lock.createStatement().executeQuery("select id from product where id=1 for update").close();
            Callable<Integer> purchase = () -> mvc.perform(post("/api/orders").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json")
                .content("{\"productId\":1,\"quantity\":1,\"requestId\":\""+java.util.UUID.randomUUID()+"\"}")).andReturn().getResponse().getStatus();
            var first = pool.submit(purchase);
            var second = pool.submit(purchase);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (db.queryForObject("select count(*) from performance_schema.data_lock_waits", Integer.class) < 2 && System.nanoTime() < deadline) Thread.sleep(50);
            assertTrue(db.queryForObject("select count(*) from performance_schema.data_lock_waits", Integer.class) >= 2, "both purchases must be waiting on the stock row");
            lock.commit();
            var statuses = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(code -> code == 200).count());
            assertEquals(1, statuses.stream().filter(code -> code == 409).count());
            assertEquals(1, db.queryForObject("select count(*) from retail_order where id>?", Integer.class, originalLastOrder));
        } finally {
            pool.shutdownNow();
            db.update("delete from retail_order where id>?", originalLastOrder);
            db.update("update product set stock=? where id=1", originalStock);
        }
    }
}
