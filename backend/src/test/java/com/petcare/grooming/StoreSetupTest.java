package com.petcare.grooming;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:store_setup;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc @Transactional
class StoreSetupTest {
    @Autowired MockMvc mvc;
    @Test void staffCanCreateBusinessRecordsAndBindARealOwner() throws Exception {
        String customer=mvc.perform(post("/api/customers").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"name\":\"新宠主\",\"phone\":\"13900139000\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int customerId=JsonPath.read(customer,"$.data.id");
        mvc.perform(post("/api/customers").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json")
            .content("{\"name\":\"新宠主\",\"phone\":\"13900139000\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/customers").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json")
            .content("{\"name\":\"重复\",\"phone\":\"13900139000\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/employees").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"name\":\"洗护师\",\"title\":\"护理师\",\"skills\":\"基础洗护\",\"species\":\"DOG\",\"sizes\":[\"SMALL\",\"MEDIUM\"]}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/resources").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json")
            .content("{\"name\":\"新洗护区\",\"species\":\"DOG\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/inventory").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"name\":\"新耗材\",\"unit\":\"ml\",\"threshold\":100}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.stock").value(0));
        mvc.perform(post("/api/accounts").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"username\":\"newowner\",\"password\":\"StrongPass123!\",\"role\":\"OWNER\",\"customerId\":"+customerId+"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/pets").with(user("newowner").roles("OWNER")).with(csrf()).contentType("application/json")
            .content("{\"name\":\"小白\",\"species\":\"DOG\",\"breed\":\"比熊\",\"ageBand\":\"YOUNG\",\"sizeType\":\"SMALL\",\"weight\":5,\"coat\":\"卷毛\",\"allergy\":\"NONE\",\"skinHistory\":\"HEALTHY\",\"groomingHistory\":\"NONE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.customer_id").value(customerId));
    }

    @Test void storeSettingsCanBeQueriedAndUpdatedByManager() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/store/settings").with(user("owner").roles("OWNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storeName").isNotEmpty());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/store/settings")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"storeName\":\"新旗舰店\",\"openTime\":\"08:30\",\"closeTime\":\"21:30\",\"bufferMinutes\":20}"))
            .andExpect(status().isForbidden());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/store/settings")
                .with(user("manager").roles("MANAGER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"storeName\":\"新旗舰店\",\"openTime\":\"08:30\",\"closeTime\":\"21:30\",\"bufferMinutes\":20}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storeName").value("新旗舰店"))
            .andExpect(jsonPath("$.data.openTime").value("08:30"))
            .andExpect(jsonPath("$.data.bufferMinutes").value(20));
    }

    @Test void notificationAndAuditEndpointsOperateCorrectly() throws Exception {
        // Notifications query by owner
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/notifications").with(user("owner").roles("OWNER")))
            .andExpect(status().isOk());

        // Notifications mark all read
        mvc.perform(post("/api/notifications/read-all").with(user("owner").roles("OWNER")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(true));

        // Audit logs restricted to ADMIN / MANAGER
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/audit-logs").with(user("owner").roles("OWNER")))
            .andExpect(status().isForbidden());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/audit-logs").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }
}
