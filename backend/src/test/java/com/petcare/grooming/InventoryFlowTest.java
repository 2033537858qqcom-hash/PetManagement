package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:inventory_flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc @Transactional
class InventoryFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Test void adjustmentsAreAuthorizedAndRecorded() throws Exception {
        String form="{\"kind\":\"IN\",\"quantity\":20,\"reason\":\"到货\",\"requestId\":\"9f4256cb-733a-4bdb-b3d0-5f19255ed343\"}";
        mvc.perform(post("/api/inventory/1/adjust").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(form)).andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/1/adjust").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(form))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.stock").value(2320));
        mvc.perform(post("/api/inventory/1/adjust").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(form))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.stock").value(2320));
        mvc.perform(post("/api/inventory/1/adjust").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"kind\":\"OUT\",\"quantity\":9999,\"reason\":\"盘亏\",\"requestId\":\"919e31d0-a50a-4b42-88ef-748f8d948e9e\"}"))
            .andExpect(status().isConflict());
        assertEquals(1,db.queryForObject("select count(*) from inventory_flow where product_id=1", Integer.class));
    }
    @Test void configuredConsumablesAreDeductedExactlyOnceWhenCareCompletes() throws Exception {
        mvc.perform(put("/api/services/1/consumption").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json")
            .content("{\"items\":[{\"productId\":1,\"quantity\":100}]}"))
            .andExpect(status().isOk());
        db.update("update grooming set stage='TRIMMING' where id=4");
        String body="{\"weight\":5.2,\"skin\":\"CLEAR\",\"coat\":\"LIGHT\",\"ear\":\"CLEAN\"}";
        mvc.perform(post("/api/grooming/4/assessment").with(user("groomer").roles("GROOMER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/grooming/4/assessment").with(user("groomer").roles("GROOMER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        assertEquals(2200, db.queryForObject("select stock from product where id=1", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from inventory_flow where product_id=1 and action='CARE'", Integer.class));
    }
    @Test void retailPackagesDeductTheirConfiguredUnitsAndRecordFlow() throws Exception {
        mvc.perform(put("/api/inventory/1/retail").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"saleUnits\":500,\"price\":68}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/orders").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content("{\"productId\":1,\"quantity\":2,\"requestId\":\"b957121f-6081-43f9-9000-ae72ae39a380\"}"))
            .andExpect(status().isOk());
        assertEquals(1300,db.queryForObject("select stock from product where id=1",Integer.class));
        assertEquals(-1000,db.queryForObject("select delta from inventory_flow where product_id=1 and action='SALE'",Integer.class));
    }
    @Test void shortageDuringCareDeductsAvailableStockWithoutBlockingCompletion() throws Exception {
        mvc.perform(put("/api/services/1/consumption").with(user("manager").roles("MANAGER")).with(csrf()).contentType("application/json")
            .content("{\"items\":[{\"productId\":1,\"quantity\":5000}]}"))
            .andExpect(status().isOk());
        db.update("update grooming set stage='TRIMMING' where id=4");
        String body="{\"weight\":5.2,\"skin\":\"CLEAR\",\"coat\":\"LIGHT\",\"ear\":\"CLEAN\"}";
        mvc.perform(post("/api/grooming/4/assessment").with(user("groomer").roles("GROOMER")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WAITING_PICKUP"));
        assertEquals(0, db.queryForObject("select stock from product where id=1", Integer.class));
    }
}
