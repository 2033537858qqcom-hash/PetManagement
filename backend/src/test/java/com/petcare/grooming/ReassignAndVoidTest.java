package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reassign_void;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
@Transactional
class ReassignAndVoidTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @Test void reassignToGroomerOnLeaveIsRejected() throws Exception {
        // Employee 2 is marked OFF for today
        db.update("insert into employee_schedule(employee_id, schedule_date, status) values (2, ?, 'OFF')", LocalDate.now());
        mvc.perform(put("/api/grooming/1/reassign")
                .with(user("manager").roles("MANAGER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"employeeId\":2}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("该洗护师今日休假，无法接单")));
    }

    @Test void reassignToAvailableGroomerSucceeds() throws Exception {
        // Grooming 1 is dog pet, employee 1 is dog groomer
        mvc.perform(put("/api/grooming/1/reassign")
                .with(user("manager").roles("MANAGER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"employeeId\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.employee_id").value(1));
        assertEquals(1L, ((Number) db.queryForMap("select employee_id from grooming where id=1").get("employee_id")).longValue());
    }

    @Test void voidGroomingCancelsGroomingAndAppointment() throws Exception {
        // Grooming 1 is in GROOMING stage BATHING
        mvc.perform(post("/api/grooming/1/void")
                .with(user("manager").roles("MANAGER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"reason\":\"客户临时有事终止\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertEquals("CANCELLED", db.queryForObject("select status from grooming where id=1", String.class));
    }

    @Test void groomerCanViewReviewsOfTheirOwnGroomings() throws Exception {
        // Employee 1 is groomer (Zhou Kai); grooming 2 is assigned to employee 1
        db.update("insert into review(customer_name, pet_name, rating, content, grooming_id) values ('林知夏', '糯米', 5, '周凯老师剪得特别好', 2)");
        mvc.perform(get("/api/reviews")
                .with(user("groomer").roles("GROOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.data[0].content", containsString("周凯老师")));
    }
}
