package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:access_isolation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
class AccessIsolationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @Test @Transactional @WithMockUser(username = "owner2", roles = "OWNER")
    void separateOwnerUsesItsOwnBindingForProfileAndData() throws Exception {
        db.update("insert into account(username,customer_id) values ('owner2',2)");
        mvc.perform(get("/api/auth/me")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customerId").value(2))
            .andExpect(jsonPath("$.data.name").value("陈思远"));
        mvc.perform(get("/api/pets")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].customer_id", everyItem(is(2))));
    }

    @Test @WithMockUser(username = "owner", roles = "OWNER")
    void ownerCannotReadOrActOnAnotherFamily() throws Exception {
        mvc.perform(get("/api/customers")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].id", everyItem(is(1))));
        mvc.perform(get("/api/pets")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].customer_id", everyItem(is(1))));
        mvc.perform(get("/api/appointments")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].customer_id", everyItem(is(1))));
        mvc.perform(get("/api/grooming")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].customer_id", everyItem(is(1))));
        mvc.perform(get("/api/pets/3/records")).andExpect(status().isForbidden());
        mvc.perform(get("/api/services/recommendations/3")).andExpect(status().isForbidden());
        mvc.perform(post("/api/appointments").with(csrf()).contentType("application/json")
            .content("{\"customerId\":2,\"petId\":3,\"serviceId\":4,\"date\":\"2099-01-01\",\"startTime\":\"10:00\"}"))
            .andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username = "groomer", roles = "GROOMER")
    void groomerOnlySeesAssignedWork() throws Exception {
        mvc.perform(get("/api/grooming")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].employee_id", everyItem(is(1))));
        mvc.perform(get("/api/appointments")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].employee_id", everyItem(is(1))));
        mvc.perform(post("/api/grooming/addons/1/decision").with(csrf()).contentType("application/json")
            .content("{\"decision\":\"APPROVED\"}")).andExpect(status().isForbidden());
    }

    @Test @Transactional @WithMockUser(username = "groomer", roles = "GROOMER")
    void groomerCanUpdateAssignedPetButNotOthers() throws Exception {
        mvc.perform(put("/api/pets/1").with(csrf()).contentType("application/json")
            .content("{\"name\":\"糯米\",\"species\":\"DOG\",\"breed\":\"比熊\",\"ageBand\":\"YOUNG\",\"sizeType\":\"SMALL\",\"weight\":5.2,\"coat\":\"卷毛\",\"allergy\":\"FRAGRANCE\",\"skinHistory\":\"HEALTHY\",\"groomingHistory\":\"MATTING\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.allergy").value("FRAGRANCE"));
        mvc.perform(put("/api/pets/3").with(csrf()).contentType("application/json")
            .content("{\"name\":\"雪球\",\"species\":\"CAT\",\"breed\":\"布偶猫\",\"ageBand\":\"YOUNG\",\"sizeType\":\"SMALL\",\"weight\":4.8,\"coat\":\"长毛\",\"allergy\":\"NONE\",\"skinHistory\":\"HEALTHY\",\"groomingHistory\":\"BASIC\"}"))
            .andExpect(status().isForbidden());
    }
    @Test @WithMockUser(username = "owner", roles = "USER")
    void unrecognizedRoleCannotReadStoreCustomerData() throws Exception {
        for (String path : new String[]{"/api/customers", "/api/pets", "/api/appointments", "/api/grooming", "/api/grooming/addons", "/api/members/cards", "/api/reviews", "/api/analytics/dashboard", "/api/services/recommendations/1"}) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }
}
