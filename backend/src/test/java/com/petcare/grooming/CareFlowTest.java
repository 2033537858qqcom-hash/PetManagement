package com.petcare.grooming;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:care_flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(CareFlowTest.TestClock.class)
class CareFlowTest {
    @org.springframework.boot.test.context.TestConfiguration static class TestClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary java.time.Clock testClock() { return java.time.Clock.fixed(LocalDate.now().atTime(14,0).atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault()); }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @Test @WithMockUser(username = "owner", roles = "OWNER")
    void ownerMaintainsOwnPetWithFixedTerms() throws Exception {
        mvc.perform(get("/api/pets")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[1].allergy").value("FRAGRANCE"))
            .andExpect(jsonPath("$.data[1].risks[0]", containsString("香波香精")))
            .andExpect(jsonPath("$.data[1].risks[1]", containsString("过敏性皮炎")));
        mvc.perform(get("/api/pets/2/records")).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(2)));
        mvc.perform(post("/api/pets").with(csrf()).contentType("application/json").content(pet("豆豆", "柯基", "YOUNG", "NONE", "HEALTHY", "NONE")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.customer_id").value(1)).andExpect(jsonPath("$.data.risks", hasSize(0)));
        mvc.perform(post("/api/pets").with(csrf()).contentType("application/json").content(pet("豆豆", "自行填写的品种", "YOUNG", "NONE", "HEALTHY", "NONE")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.breed").value("自行填写的品种"));
        mvc.perform(post("/api/pets").with(csrf()).contentType("application/json").content(pet("豆豆", "", "YOUNG", "NONE", "HEALTHY", "NONE")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("品种")));
        mvc.perform(put("/api/pets/2").with(csrf()).contentType("application/json").content(pet("旺财", "金毛", "ADULT", "NONE", "HEALTHY", "SPA")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.risks", hasSize(0)));
        mvc.perform(put("/api/pets/3").with(csrf()).contentType("application/json").content(pet("雪球", "布偶猫", "YOUNG", "NONE", "HEALTHY", "BASIC").replace("DOG", "CAT")))
            .andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username = "owner", roles = "USER")
    void otherRolesCannotEditPetsOrBuy() throws Exception {
        mvc.perform(post("/api/pets").with(csrf()).contentType("application/json").content(pet("豆豆", "柯基", "YOUNG", "NONE", "HEALTHY", "NONE"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content("{\"productId\":1,\"quantity\":1}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/grooming").with(csrf()).contentType("application/json").content("{\"petId\":1,\"serviceId\":1,\"employeeId\":1}")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username = "owner", roles = "OWNER")
    void ownerCancelsOwnConfirmedAppointmentAndPlacesOrder() throws Exception {
        mvc.perform(post("/api/appointments/4/cancel").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(post("/api/appointments/4/cancel").with(csrf())).andExpect(status().isConflict());
        mvc.perform(post("/api/appointments/1/cancel").with(csrf())).andExpect(status().isConflict());
        mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content("{\"productId\":1,\"quantity\":2,\"requestId\":\"704d2a34-aac4-4306-a958-5f6bd4f44e58\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.amount").value(136.0)).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mvc.perform(get("/api/orders")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].product_name").value("燕麦低敏香波"));
        mvc.perform(get("/api/inventory")).andExpect(jsonPath("$.data[0].stock").value(2298));
        mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content("{\"productId\":1,\"quantity\":9}")).andExpect(status().isBadRequest());
    }

    @Test @WithMockUser(username = "manager", roles = "MANAGER")
    void managerCanCancelAnyBookingAndOpenAWalkIn() throws Exception {
        mvc.perform(post("/api/appointments/3/cancel").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(post("/api/grooming").with(csrf()).contentType("application/json").content("{\"petId\":2,\"serviceId\":1,\"employeeId\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stage").value("CHECK_IN"))
            .andExpect(jsonPath("$.data.pet_name").value("旺财"))
            .andExpect(jsonPath("$.data.appointment_id").isNumber())
            .andExpect(jsonPath("$.data.resource_id").isNumber());
        mvc.perform(put("/api/schedules/2/" + LocalDate.now()).with(csrf()).contentType("application/json").content("{\"status\":\"OFF\"}"))
            .andExpect(status().isConflict());
        mvc.perform(put("/api/schedules/3/" + LocalDate.now()).with(csrf()).contentType("application/json").content("{\"status\":\"OFF\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/grooming").with(csrf()).contentType("application/json").content("{\"petId\":3,\"serviceId\":4,\"employeeId\":3}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/pets").with(csrf()).contentType("application/json")
            .content("{\"customerId\":2,\"name\":\"小柴\",\"species\":\"DOG\",\"breed\":\"柴犬\",\"ageBand\":\"YOUNG\",\"sizeType\":\"MEDIUM\",\"weight\":9.65,\"coat\":\"短毛\",\"allergy\":\"NONE\",\"skinHistory\":\"HEALTHY\",\"groomingHistory\":\"BASIC\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customer_id").value(2))
            .andExpect(jsonPath("$.data.breed").value("柴犬"))
            .andExpect(jsonPath("$.data.weight").value(9.65));
    }

    @Test @WithMockUser(username = "manager", roles = "MANAGER")
    void managerControlsPriceAndAvailability() throws Exception {
        String created = mvc.perform(post("/api/services").with(csrf()).contentType("application/json")
                .content("{\"name\":\"幼犬洗护\",\"category\":\"基础洗护\",\"price\":88,\"duration\":60,\"species\":\"DOG\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true)).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.data.id");
        mvc.perform(put("/api/services/" + id).with(csrf()).contentType("application/json").content("{\"price\":96.5,\"enabled\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.price").value(96.5)).andExpect(jsonPath("$.data.enabled").value(false));
        String date = LocalDate.now().plusDays(7).toString();
        mvc.perform(post("/api/appointments").with(csrf()).contentType("application/json")
                .content("{\"customerId\":1,\"petId\":1,\"serviceId\":" + id + ",\"date\":\"" + date + "\",\"startTime\":\"16:00\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("停用")));
    }

    @Test @WithMockUser(username = "groomer", roles = "GROOMER")
    void groomerScoresAssignedOrderFromFixedTerms() throws Exception {
        db.update("update grooming set stage='TRIMMING' where id=4");
        mvc.perform(post("/api/grooming/1/assessment").with(csrf()).contentType("application/json")
                .content("{\"weight\":26,\"skin\":\"CLEAR\",\"coat\":\"LIGHT\",\"ear\":\"CLEAN\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/grooming/4/assessment").with(csrf()).contentType("application/json")
                .content("{\"weight\":5,\"skin\":\"CLEAR\",\"coat\":\"LIGHT\",\"ear\":\"CLEAN\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(93)).andExpect(jsonPath("$.data.status").value("WAITING_PICKUP"));
        mvc.perform(post("/api/grooming/4/assessment").with(csrf()).contentType("application/json")
                .content("{\"weight\":5,\"skin\":\"CLEAR\",\"coat\":\"LIGHT\",\"ear\":\"CLEAN\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/orders")).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
    }

    private String pet(String name, String breed, String age, String allergy, String skin, String history) {
        return "{\"name\":\"" + name + "\",\"species\":\"DOG\",\"breed\":\"" + breed + "\",\"ageBand\":\"" + age + "\",\"sizeType\":\"SMALL\",\"weight\":8,\"coat\":\"短毛\",\"allergy\":\"" + allergy + "\",\"skinHistory\":\"" + skin + "\",\"groomingHistory\":\"" + history + "\"}";
    }
}
