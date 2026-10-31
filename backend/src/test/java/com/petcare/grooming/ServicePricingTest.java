package com.petcare.grooming;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:pricing;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc @Transactional
class ServicePricingTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate db;
    @Test void sizeRulesDrivePriceAndDurationButReschedulingKeepsTheOriginalQuote() throws Exception {
        String rules="{\"items\":[{\"sizeType\":\"LARGE\",\"price\":210,\"duration\":90}]}";
        mvc.perform(put("/api/services/1/prices").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(rules)).andExpect(status().isForbidden());
        mvc.perform(put("/api/services/1/prices").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content(rules)).andExpect(status().isOk());
        String date=LocalDate.now().plusDays(30).toString();
        mvc.perform(post("/api/appointments").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json")
            .content("{\"customerId\":1,\"petId\":2,\"serviceId\":1,\"date\":\""+date+"\",\"startTime\":\"10:00\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.price").value(210)).andExpect(jsonPath("$.data.endTime").value("11:30"));
        long id=db.queryForObject("select max(id) from appointment",Long.class);
        mvc.perform(put("/api/services/1/prices").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json")
            .content("{\"items\":[{\"sizeType\":\"LARGE\",\"price\":250,\"duration\":120}]}" )).andExpect(status().isOk());
        mvc.perform(post("/api/appointments/"+id+"/reschedule").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json")
            .content("{\"date\":\""+date+"\",\"startTime\":\"13:00\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.price").value(210)).andExpect(jsonPath("$.data.end_time").value("14:30:00"));
    }
}
