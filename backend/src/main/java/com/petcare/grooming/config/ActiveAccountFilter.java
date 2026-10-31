package com.petcare.grooming.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

final class ActiveAccountFilter extends OncePerRequestFilter {
    private record Version(boolean enabled, long value) {}
    private final JdbcTemplate db;
    ActiveAccountFilter(JdbcTemplate db) { this.db = db; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            var accounts = db.query("select enabled,account_version from account where username=?", (rs, row) -> new Version(rs.getBoolean(1),rs.getLong(2)), auth.getName());
            var session = request.getSession(false);
            Object version = session == null ? null : session.getAttribute("account_version");
            if (accounts.isEmpty() || !accounts.get(0).enabled() || (version instanceof Number number && number.longValue()!=accounts.get(0).value())) {
                SecurityContextHolder.clearContext();
                if (session != null) session.invalidate();
                response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"账号已停用，请重新登录\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
