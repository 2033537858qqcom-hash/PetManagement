package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:walk_in_late;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(WalkInLateNightTest.TestClock.class)
class WalkInLateNightTest {
    @Autowired MockMvc mvc;

    @org.springframework.boot.test.context.TestConfiguration static class TestClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary java.time.Clock testClock() {
            return java.time.Clock.fixed(LocalDate.now().atTime(21, 30).atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault());
        }
    }

    @Test void lateNightWalkInIsRejectedRatherThanReversingClock() throws Exception {
        mvc.perform(post("/api/grooming")
                .with(user("reception").roles("RECEPTION"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"petId\":2,\"serviceId\":1,\"employeeId\":2}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("当前营业时间不足以完成该服务")));
    }
}
