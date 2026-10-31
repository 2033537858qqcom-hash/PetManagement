package com.petcare.grooming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:registration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
class RegistrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    static final String PASSWORD = "StrongPass123!";

    private Map<String, Object> form(String username, String phone) {
        return new HashMap<>(Map.of("username", username, "password", PASSWORD, "name", "夏小满", "phone", phone));
    }
    private int customers() { return db.queryForObject("select count(*) from customer", Integer.class); }

    @Test void emailUsernameCanRegisterWithInternationalPhone() throws Exception {
        var body = form("2033537858qq.com@gmail.com", "+8618736134699");
        body.put("name", "李");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("2033537858qq.com@gmail.com"))
            .andExpect(jsonPath("$.data.role").value("宠主")).andExpect(jsonPath("$.data.name").value("李"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", "2033537858qq.com@Gmail.com", "password", PASSWORD))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("2033537858qq.com@gmail.com"));
    }

    @Test void registrationCreatesAnIsolatedOwnerAndAnAuthenticatedSession() throws Exception {
        var result = mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(form("signup_owner", "13900139001"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("signup_owner"))
            .andExpect(jsonPath("$.data.role").value("宠主")).andExpect(jsonPath("$.data.name").value("夏小满"))
            .andExpect(jsonPath("$.data.title").value("普通会员"))
            .andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.password_hash").doesNotExist())
            .andReturn();
        long customerId = db.queryForObject("select customer_id from account where username='signup_owner'", Long.class);
        assertTrue(customerId > 3);
        assertEquals(0, db.queryForObject("select balance from customer where id=?", java.math.BigDecimal.class, customerId).signum());
        assertEquals(0, db.queryForObject("select points from customer where id=?", Integer.class, customerId));
        String hash = db.queryForObject("select password_hash from account where username='signup_owner'", String.class);
        assertNotEquals(PASSWORD, hash);
        assertTrue(encoder.matches(PASSWORD, hash));
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        assertNotNull(session.getAttribute("account_version"));
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customerId").value(customerId));
        mvc.perform(get("/api/customers").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].id").value(customerId));
        mvc.perform(get("/api/pets").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/pets/1/records").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", "signup_owner", "password", PASSWORD))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.customerId").value(customerId));
    }

    @Test void registrationIgnoresRequestedPrivilegesAndExistingCustomerBindings() throws Exception {
        var body = form("signup_privileges", "13900139002");
        body.put("role", "ADMIN"); body.put("customerId", 1); body.put("employeeId", 1);
        body.put("balance", 99999); body.put("points", 99999);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("宠主"));
        var account = db.queryForMap("select role_code,customer_id,employee_id from account where username='signup_privileges'");
        assertEquals("OWNER", account.get("role_code"));
        assertNotEquals(1L, ((Number) account.get("customer_id")).longValue());
        assertNull(account.get("employee_id"));
    }

    @Test void duplicateUsernameLeavesNoOrphanCustomer() throws Exception {
        int before = customers();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(form("manager", "13900139003"))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("账号已存在")));
        assertEquals(before, customers());
        assertEquals(0, db.queryForObject("select count(*) from customer where phone='13900139003'", Integer.class));
    }

    @Test void duplicatePhoneCannotClaimAnExistingCustomer() throws Exception {
        int before = customers();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(form("signup_duplicate_phone", "13800138001"))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("联系电话")));
        assertEquals(before, customers());
        assertEquals(0, db.queryForObject("select count(*) from account where username='signup_duplicate_phone'", Integer.class));
    }

    @Test void normalizesSurroundingWhitespace() throws Exception {
        var body = form("  signup_trimmed  ", " 13900139004 "); body.put("name", " 夏小满 ");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("signup_trimmed"))
            .andExpect(jsonPath("$.data.name").value("夏小满"));
        assertEquals("13900139004", db.queryForObject("select phone from customer where name='夏小满' and phone='13900139004'", String.class));
    }

    @Test void registrationRequiresCsrf() throws Exception {
        int before = customers();
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(json.writeValueAsString(form("signup_no_csrf", "13900139005"))))
            .andExpect(status().isForbidden());
        assertEquals(before, customers());
    }

    static Stream<Arguments> invalidForms() {
        return Stream.of(
            Arguments.of("username", null, "账号"), Arguments.of("username", "ab", "账号"),
            Arguments.of("username", "a".repeat(33), "账号"), Arguments.of("username", "bad name", "账号"),
            Arguments.of("password", null, "密码"), Arguments.of("password", "short", "密码"),
            Arguments.of("password", " ".repeat(12), "密码"), Arguments.of("password", "a".repeat(73), "密码"),
            Arguments.of("password", "宠".repeat(25), "密码"),
            Arguments.of("name", null, "姓名"), Arguments.of("name", "   ", "姓名"), Arguments.of("name", "夏".repeat(61), "姓名"),
            Arguments.of("phone", null, "联系电话"), Arguments.of("phone", "not-a-phone", "联系电话"),
            Arguments.of("phone", "12345", "联系电话"), Arguments.of("phone", "1".repeat(21), "联系电话")
        );
    }

    @ParameterizedTest @MethodSource("invalidForms")
    void rejectsInvalidFieldsWithoutCreatingData(String field, Object value, String message) throws Exception {
        int before = customers();
        var body = form("signup_invalid", "13900139006"); body.put(field, value);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString(message)));
        assertEquals(before, customers());
    }

    @Test void concurrentRegistrationsDoNotLeaveOrphanCustomers() throws Exception {
        int before = customers();
        var pool = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(1);
        try {
            var attempts = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 4; i++) {
                String body = json.writeValueAsString(form("signup_race", "1390013910" + i));
                attempts.add(pool.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
                        .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var attempt : attempts) statuses.add(attempt.get(20, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(code -> code == 200).count());
            assertEquals(3, statuses.stream().filter(code -> code == 409).count());
            assertEquals(before + 1, customers());
        } finally { pool.shutdownNow(); }
    }
}
