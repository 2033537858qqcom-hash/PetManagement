package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc @Transactional
class AccountManagementTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Test void passwordsCannotExceedBcryptByteLimit() throws Exception {
        String body="{\"username\":\"unicodeowner\",\"password\":\""+"宠".repeat(30)+"\",\"role\":\"OWNER\",\"customerId\":2}";
        mvc.perform(post("/api/accounts").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    @Test void adminCreatesBoundAccountAndCanDisableIt() throws Exception {
        String body="{\"username\":\"owner2\",\"password\":\"StrongPass123!\",\"role\":\"OWNER\",\"customerId\":2}";
        mvc.perform(post("/api/accounts").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/accounts").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("owner2"));
        assertNotEquals("StrongPass123!", db.queryForObject("select password_hash from account where username='owner2'", String.class));
        var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"owner2\",\"password\":\"StrongPass123!\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.customerId").value(2)).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(put("/api/accounts/owner2/password").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"password\":\"StrongPass456!\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/pets").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"owner2\",\"password\":\"StrongPass123!\"}"))
            .andExpect(status().isUnauthorized());
        var nextLogin = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"owner2\",\"password\":\"StrongPass456!\"}"))
            .andExpect(status().isOk()).andReturn();
        var nextSession = (MockHttpSession) nextLogin.getRequest().getSession(false);
        mvc.perform(put("/api/accounts/owner2/status").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"enabled\":false}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"owner2\",\"password\":\"StrongPass456!\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pets").session(nextSession)).andExpect(status().isUnauthorized());
    }

    @Test void adminCanDeleteBoundAccountToAllowRebinding() throws Exception {
        String body1 = "{\"username\":\"owner_bind_test\",\"password\":\"StrongPass123!\",\"role\":\"OWNER\",\"customerId\":2}";
        mvc.perform(post("/api/accounts").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body1))
            .andExpect(status().isOk());
        // duplicate customerId is rejected
        String body2 = "{\"username\":\"owner_bind_test2\",\"password\":\"StrongPass123!\",\"role\":\"OWNER\",\"customerId\":2}";
        mvc.perform(post("/api/accounts").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body2))
            .andExpect(status().isConflict());

        // delete the first account
        mvc.perform(delete("/api/accounts/owner_bind_test").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());

        // now customerId 2 can be rebound to a new account
        mvc.perform(post("/api/accounts").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("owner_bind_test2"));
    }
}
