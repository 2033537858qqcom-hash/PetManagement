package com.petcare.grooming;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:addon_request;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc @Transactional
class AddonRequestTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate db;
    @Test void assignedGroomerRequestsAddonOnceAndOwnerApprovalIsIncludedInSettlement() throws Exception {
        db.update("update grooming set stage='TRIMMING' where id=4");
        db.update("insert into account(username,customer_id) values ('owner2',2)");
        String body="{\"name\":\"低敏护理\",\"reason\":\"皮肤敏感\",\"amount\":30,\"requestId\":\"0f0a1c4b-26b7-44a8-a003-8bf3de4ae123\"}";
        mvc.perform(post("/api/grooming/4/addons").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        String created=mvc.perform(post("/api/grooming/4/addons").with(user("groomer").roles("GROOMER")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING")).andReturn().getResponse().getContentAsString();
        int id=JsonPath.read(created,"$.data.id");
        mvc.perform(post("/api/grooming/4/addons").with(user("groomer").roles("GROOMER")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(post("/api/grooming/addons/"+id+"/decision").with(user("owner2").roles("OWNER")).with(csrf()).contentType("application/json").content("{\"decision\":\"APPROVED\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/grooming/addons/"+id+"/decision").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content("{\"decision\":\"APPROVED\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/grooming/4/assessment").with(user("groomer").roles("GROOMER")).with(csrf()).contentType("application/json").content("{\"weight\":5.2,\"skin\":\"CLEAR\",\"coat\":\"SMOOTH\",\"ear\":\"CLEAN\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/grooming/4/settle").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content("{\"method\":\"CASH\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.amount").value(128));
        assertEquals(1,db.queryForObject("select count(*) from addon where request_id=?",Integer.class,"0f0a1c4b-26b7-44a8-a003-8bf3de4ae123"));
    }

    @Test void cannotApproveAddonWhenGroomingIsCompletedOrPaid() throws Exception {
        db.update("insert into addon(grooming_id,name,reason,amount,status,request_id) values (2,'耳部深层护理','清洁',25.00,'PENDING','11111111-2222-3333-4444-555555555555')");
        long addonId = db.queryForObject("select id from addon where request_id='11111111-2222-3333-4444-555555555555'", Long.class);

        // First test: grooming is completed
        db.update("update grooming set status='COMPLETED' where id=2");
        mvc.perform(post("/api/grooming/addons/" + addonId + "/decision")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("工单已结束")));

        // Second test: grooming is WAITING_PICKUP, but settlement is PAID
        db.update("update grooming set status='WAITING_PICKUP' where id=2");
        db.update("insert into service_settlement(grooming_id,customer_id,base_amount,addon_amount,amount,method,status,operator_name,paid_at) values (2,1,100,0,100,'CASH','PAID','reception',now())");
        mvc.perform(post("/api/grooming/addons/" + addonId + "/decision")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("已完成结算")));
    }
}
