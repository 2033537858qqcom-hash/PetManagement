package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named="mysql.browser.db", matches="codex_pet_browser_[a-z0-9]+")
@ActiveProfiles({"mysql","mysql-demo"}) @SpringBootTest @AutoConfigureMockMvc
class MySqlRestartTest {
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry props) {
        String name=System.getProperty("mysql.browser.db", "");
        if(!name.matches("codex_pet_browser_[a-z0-9]+")) throw new IllegalStateException("Expected isolated test database");
        props.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:13318/"+name+"?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false");
        props.add("spring.datasource.username", () -> "root");
        props.add("spring.datasource.password", () -> "");
        props.add("pet-grooming.demo-seed-enabled", () -> "true");
    }
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Test void browserChangesSurviveApplicationRestart() throws Exception {
        assertEquals("COMPLETED",db.queryForObject("select status from appointment where id=3",String.class));
        assertEquals("PAID",db.queryForObject("select s.status from service_settlement s join grooming g on g.id=s.grooming_id where g.appointment_id=3",String.class));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"username\":\"owner2\",\"password\":\"StrongPass123!\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.customerId").value(2));
    }
}
