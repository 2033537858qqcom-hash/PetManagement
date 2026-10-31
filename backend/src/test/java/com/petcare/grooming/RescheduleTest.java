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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:reschedule;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc @Transactional
class RescheduleTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate db;
    @Test void ownerCanRescheduleOnlyOwnConfirmedAppointmentAndPriceIsPreserved() throws Exception {
        db.update("insert into account(username,customer_id) values ('owner2',2)");
        String date=LocalDate.now().plusDays(25).toString();
        String body="{\"date\":\""+date+"\",\"startTime\":\"10:00\"}";
        mvc.perform(post("/api/appointments/4/reschedule").with(user("owner2").roles("OWNER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/appointments/4/reschedule").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(4)).andExpect(jsonPath("$.data.appointment_date").value(date)).andExpect(jsonPath("$.data.price").value(148));
        mvc.perform(post("/api/appointments/1/reschedule").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
    }
}
