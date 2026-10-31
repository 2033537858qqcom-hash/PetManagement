package com.petcare.grooming;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:employee_schedule;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc @Transactional
class EmployeeScheduleTest {
    @Autowired MockMvc mvc;
    @Test void bookingsRespectLeaveAndShiftAndExistingBookingsPreventUnsafeChanges() throws Exception {
        String date=LocalDate.now().plusDays(20).toString();
        String off="{\"status\":\"OFF\"}";
        mvc.perform(put("/api/schedules/1/"+date).with(user("groomer").roles("GROOMER")).with(csrf()).contentType("application/json").content(off)).andExpect(status().isForbidden());
        for(int id:new int[]{1,2}) mvc.perform(put("/api/schedules/"+id+"/"+date).with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content(off)).andExpect(status().isOk());
        String at9="{\"customerId\":1,\"petId\":1,\"serviceId\":1,\"date\":\""+date+"\",\"startTime\":\"09:00\"}";
        mvc.perform(post("/api/appointments").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content(at9)).andExpect(status().isConflict());
        mvc.perform(put("/api/schedules/1/"+date).with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content("{\"status\":\"WORK\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"}"))
            .andExpect(status().isOk());
        String late="{\"customerId\":1,\"petId\":2,\"serviceId\":1,\"date\":\""+date+"\",\"startTime\":\"11:15\"}";
        mvc.perform(post("/api/appointments").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content(late)).andExpect(status().isConflict());
        mvc.perform(post("/api/appointments").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content(at9)).andExpect(status().isOk());
        mvc.perform(put("/api/schedules/1/"+date).with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json").content(off)).andExpect(status().isConflict());
    }
}
