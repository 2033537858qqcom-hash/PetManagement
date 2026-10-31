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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named="mysql.bootstrap.integration",matches="true")
@ActiveProfiles("mysql")
@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://127.0.0.1:13318/codex_pet_bootstrap_b03?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false", "spring.datasource.username=root", "spring.datasource.password=", "pet-grooming.demo-seed-enabled=false", "pet-grooming.bootstrap-admin-password=StrongBootstrap123!"})
@AutoConfigureMockMvc
class MySqlBootstrapTest {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Test void productionBootstrapHasNoDemoCustomersOrDemoLogins() throws Exception {
        assertEquals(0,db.queryForObject("select count(*) from customer",Integer.class));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"admin\",\"password\":\"StrongBootstrap123!\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"owner\",\"password\":\"petcare123\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test @Transactional void emptyStoreCanCreateTheRecordsNeededToOperate() throws Exception {
        String customer=mvc.perform(post("/api/customers").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"name\":\"张女士\",\"phone\":\"13900139000\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int id=com.jayway.jsonpath.JsonPath.read(customer,"$.data.id");
        mvc.perform(post("/api/employees").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"name\":\"小周\",\"title\":\"护理师\",\"skills\":\"基础洗护\",\"species\":\"DOG\",\"sizes\":[\"SMALL\"]}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/resources").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"name\":\"一号工位\",\"species\":\"DOG\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/inventory").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"name\":\"香波\",\"unit\":\"ml\",\"threshold\":100}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/services").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"name\":\"基础洗护\",\"category\":\"基础洗护\",\"price\":98,\"duration\":60,\"species\":\"DOG\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/accounts").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"username\":\"newowner\",\"password\":\"StrongPass123!\",\"role\":\"OWNER\",\"customerId\":"+id+"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/pets").with(user("newowner").roles("OWNER")).with(csrf()).contentType("application/json")
            .content("{\"name\":\"小白\",\"species\":\"DOG\",\"breed\":\"比熊\",\"ageBand\":\"YOUNG\",\"sizeType\":\"SMALL\",\"weight\":5,\"coat\":\"卷毛\",\"allergy\":\"NONE\",\"skinHistory\":\"HEALTHY\",\"groomingHistory\":\"NONE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.customer_id").value(id));
    }
}
