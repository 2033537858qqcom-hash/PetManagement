package com.petcare.grooming;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:appointment_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(username="manager", roles="MANAGER")
@org.springframework.context.annotation.Import(AppointmentTest.TestClock.class)
class AppointmentTest {
 @Autowired MockMvc mvc;
 @org.springframework.boot.test.context.TestConfiguration static class TestClock {
  @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary java.time.Clock testClock() { return java.time.Clock.fixed(LocalDate.now().atTime(15,0).atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault()); }
 }
 private String booking(String date, String time) { return "{\"customerId\":2,\"petId\":3,\"serviceId\":4,\"date\":\""+date+"\",\"startTime\":\""+time+"\"}"; }
 @Test void catResourceAndCleaningBufferAreEnforced() throws Exception {
  String date=LocalDate.now().plusDays(7).toString();
  mvc.perform(post("/api/appointments").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(booking(date,"09:00"))).andExpect(status().isOk());
  mvc.perform(post("/api/appointments").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(booking(date,"10:15"))).andExpect(status().isConflict());
  mvc.perform(post("/api/appointments").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(booking(date,"10:30"))).andExpect(status().isOk());
 }
 @Test void cannotBookEarlierToday() throws Exception {
  String today=LocalDate.now().toString();
  mvc.perform(post("/api/appointments").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(booking(today,"09:00"))).andExpect(status().isBadRequest());
 }
 @Test void samePetCannotBeBookedForOverlappingServicesEvenWhenOtherStaffAreFree() throws Exception {
  String date=LocalDate.now().plusDays(12).toString();
  String body="{\"customerId\":1,\"petId\":1,\"serviceId\":1,\"date\":\""+date+"\",\"startTime\":\"09:00\"}";
  mvc.perform(post("/api/appointments").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
  mvc.perform(post("/api/appointments").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
 }
}



