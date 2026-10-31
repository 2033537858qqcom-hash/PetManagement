package com.petcare.grooming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:auth_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
class AuthTest {
    @Autowired MockMvc mvc;
    @Test void viteFallbackPortIsAllowedByCors() throws Exception {
        mvc.perform(get("/api/auth/csrf").header("Origin", "http://127.0.0.1:5174"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5174"));
        mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5174").with(csrf())
            .contentType("application/json").content("{\"username\":\"manager\",\"password\":\"petcare123\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
        mvc.perform(get("/api/auth/csrf").header("Origin", "http://evil.example"))
            .andExpect(status().isForbidden());
    }

    @Test void csrfTokenEndpointCanBeUsedForRealLogin() throws Exception {
        var response = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) response.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        response = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.data.token");
        String header = com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.data.header");
        mvc.perform(post("/api/auth/login").session(session).header(header, token).contentType("application/json")
            .content("{\"username\":\"admin\",\"password\":\"petcare123\"}"))
            .andExpect(status().isOk());
    }
    @Test void sessionRequiresPasswordAndExpiresOnLogout() throws Exception {
        mvc.perform(get("/api/pets")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"manager\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"manager\",\"password\":\"petcare123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("店长")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/pets").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/pets").session(new MockHttpSession())).andExpect(status().isUnauthorized());
    }

    @Test void loginIdentifiesEachRole() throws Exception {
        expectAccount("reception", "前台", "陈雨桐", "前台接待", null, null);
        expectAccount("groomer", "洗护师", "周凯", "首席美容师", null, 1);
        expectAccount("owner", "宠主", "林知夏", "金卡会员", 1, null);
        expectAccount("admin", "管理员", "系统管理员", "系统管理", null, null);
    }

    private void expectAccount(String username, String role, String name, String title, Integer customerId, Integer employeeId) throws Exception {
        var body = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"" + username + "\",\"password\":\"petcare123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(username)).andExpect(jsonPath("$.data.role").value(role))
            .andExpect(jsonPath("$.data.name").value(name)).andExpect(jsonPath("$.data.title").value(title));
        if (customerId == null) body.andExpect(jsonPath("$.data.customerId").value(nullValue()));
        else body.andExpect(jsonPath("$.data.customerId").value(customerId));
        if (employeeId == null) body.andExpect(jsonPath("$.data.employeeId").value(nullValue()));
        else body.andExpect(jsonPath("$.data.employeeId").value(employeeId));
    }
}
