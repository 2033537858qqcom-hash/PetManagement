package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:weight_recording;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
class WeightRecordingTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Test @WithMockUser(username = "groomer", roles = "GROOMER")
    void recordsActualDecimalWeight() throws Exception {
        db.update("update grooming set stage='TRIMMING' where id=4");
        mvc.perform(post("/api/grooming/4/assessment").with(csrf()).contentType("application/json")
            .content("{\"weight\":5.2,\"skin\":\"CLEAR\",\"coat\":\"LIGHT\",\"ear\":\"CLEAN\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.weight").value(5.2));
    }

    @Test
    void independentHealthRecordEntryUpdatesPetAndCreatesRecord() throws Exception {
        mvc.perform(post("/api/pets/1/records")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"weight\":4.85,\"note\":\"疫苗后称重检查\",\"skinCode\":\"CLEAR\",\"coatCode\":\"SMOOTH\",\"earCode\":\"CLEAN\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.weight").value(4.85))
            .andExpect(jsonPath("$.data.groomer_note").value("疫苗后称重检查"));

        org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal("4.85").compareTo(db.queryForObject("select weight from pet where id=1", java.math.BigDecimal.class)));
    }
}
