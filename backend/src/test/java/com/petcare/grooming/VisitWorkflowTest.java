package com.petcare.grooming;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:visit_flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
@Transactional
class VisitWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @Test void appointmentBecomesOneOrderAndProgressesToVerifiedPickup() throws Exception {
        db.update("update appointment set employee_id=1, resource_id=1, appointment_date=CURRENT_DATE where id=4");
        mvc.perform(post("/api/appointments/4/check-in").with(user("owner").roles("OWNER")).with(csrf())).andExpect(status().isForbidden());
        String json = mvc.perform(post("/api/appointments/4/check-in").with(user("reception").roles("RECEPTION")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.appointment_id").value(4))
            .andExpect(jsonPath("$.data.stage").value("CHECK_IN")).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(json, "$.data.id");
        String code = JsonPath.read(json, "$.data.pickup_code");
        mvc.perform(post("/api/appointments/4/check-in").with(user("reception").roles("RECEPTION")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(post("/api/grooming/"+id+"/assessment").with(user("groomer").roles("GROOMER")).with(csrf()).contentType("application/json")
            .content("{\"weight\":28.3,\"skin\":\"CLEAR\",\"coat\":\"LIGHT\",\"ear\":\"CLEAN\"}")).andExpect(status().isConflict());
        for (String stage : new String[]{"BATHING", "DRYING", "TRIMMING"}) {
            mvc.perform(post("/api/grooming/"+id+"/advance").with(user("groomer").roles("GROOMER")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stage").value(stage));
        }
        mvc.perform(post("/api/grooming/"+id+"/assessment").with(user("groomer").roles("GROOMER")).with(csrf()).contentType("application/json")
            .content("{\"weight\":28.3,\"skin\":\"CLEAR\",\"coat\":\"LIGHT\",\"ear\":\"CLEAN\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WAITING_PICKUP"));
        mvc.perform(post("/api/grooming/"+id+"/handover").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"pickupCode\":\"000000\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/grooming/"+id+"/handover").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"pickupCode\":\""+code+"\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/grooming/"+id+"/settle").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"method\":\"CASH\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));
        mvc.perform(post("/api/grooming/"+id+"/settle").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"method\":\"CASH\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/grooming/"+id+"/handover").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"pickupCode\":\""+code+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(get("/api/grooming/"+id+"/events").with(user("owner").roles("OWNER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(6));
    }
}
