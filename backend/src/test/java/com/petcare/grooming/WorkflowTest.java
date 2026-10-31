package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(username="owner", roles="OWNER")
class WorkflowTest {
    @Autowired MockMvc mvc;
    @Test @org.springframework.security.test.context.support.WithMockUser(username="manager",roles="MANAGER") void dashboardShowsSeededData() throws Exception {
        mvc.perform(get("/api/analytics/dashboard")).andExpect(status().isOk()).andExpect(jsonPath("$.data.todayAppointments").isNumber());
    }
    @Test void addonCanOnlyBeDecidedOnce() throws Exception {
        mvc.perform(post("/api/grooming/addons/1/decision").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"decision\":\"APPROVED\"}"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        mvc.perform(post("/api/grooming/addons/1/decision").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"decision\":\"REJECTED\"}"))
           .andExpect(status().isConflict());
    }
}
