package com.petcare.grooming.security;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AccountAccess {
    private final JdbcTemplate db;
    public AccountAccess(JdbcTemplate db) { this.db = db; }

    public boolean role(String name) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> ("ROLE_" + name).equals(a.getAuthority()));
    }
    public String currentRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        for (var a : auth.getAuthorities()) {
            if (a.getAuthority().startsWith("ROLE_")) {
                return a.getAuthority().substring(5);
            }
        }
        return null;
    }
    private long binding(String column) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "未绑定业务身份");
        var values = db.query("select " + column + " from account where username=?", (rs, n) -> (Number) rs.getObject(1), auth.getName());
        if (values.isEmpty() || values.get(0) == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "未绑定业务身份");
        return values.get(0).longValue();
    }
    public long customerId() { return binding("customer_id"); }
    public long employeeId() { return binding("employee_id"); }
    public void require(boolean allowed) { if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有操作权限"); }
    public void requireStaff() { require(role("MANAGER") || role("ADMIN") || role("RECEPTION")); }
    public void requirePet(long petId) {
        if (role("OWNER")) {
            Integer count = db.queryForObject("select count(*) from pet where id=? and customer_id=?", Integer.class, petId, customerId());
            require(count != null && count > 0);
        } else if (role("GROOMER")) {
            Integer count = db.queryForObject("select (select count(*) from grooming where pet_id=? and employee_id=?) + (select count(*) from appointment where pet_id=? and employee_id=?)", Integer.class, petId, employeeId(), petId, employeeId());
            require(count != null && count > 0);
        } else requireStaff();
    }
}
