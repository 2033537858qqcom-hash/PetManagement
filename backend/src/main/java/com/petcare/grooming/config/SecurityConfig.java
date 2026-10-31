package com.petcare.grooming.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import com.petcare.grooming.security.PasswordPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean @DependsOnDatabaseInitialization UserDetailsService users(JdbcTemplate db, PasswordEncoder encoder, @Value("${pet-grooming.seed-password:${pet-grooming.demo-password:petcare123}}") String password, @Value("${pet-grooming.seed-enabled:${pet-grooming.demo-seed-enabled:true}}") boolean seedEnabled, @Value("${pet-grooming.bootstrap-admin-password:}") String bootstrap) {
        if (seedEnabled) {
            String hash = encoder.encode(password);
            db.update("update account set password_hash=? where password_hash is null and username in ('manager','reception','groomer','owner','admin')", hash);
        } else if (!bootstrap.isBlank()) {
            if (!PasswordPolicy.valid(bootstrap)) throw new IllegalStateException("PET_BOOTSTRAP_ADMIN_PASSWORD must contain at least 12 characters and at most 72 UTF-8 bytes");
            db.update("update account set password_hash=? where username='admin' and password_hash is null", encoder.encode(bootstrap));
        }
        Integer admins = db.queryForObject("select count(*) from account where role_code='ADMIN' and enabled=1 and password_hash is not null", Integer.class);
        if (admins == null || admins == 0) throw new IllegalStateException("Set PET_BOOTSTRAP_ADMIN_PASSWORD to initialize the administrator account");
        return username -> {
            var accounts = db.query("select username,password_hash,role_code,enabled from account where username=?", (rs, n) -> {
                String passwordHash = rs.getString("password_hash"), role = rs.getString("role_code");
                if (passwordHash == null || role == null) throw new UsernameNotFoundException("账号未配置");
                return User.withUsername(rs.getString("username")).password(passwordHash).roles(role).disabled(!rs.getBoolean("enabled")).build();
            }, username);
            if (accounts.isEmpty()) throw new UsernameNotFoundException("账号不存在");
            return accounts.get(0);
        };
    }

    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception { return config.getAuthenticationManager(); }

    @Bean SecurityFilterChain security(HttpSecurity http, JdbcTemplate db) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/media/photos", "/api/media/photos/*/image", "/api/media/products/*/image", "/api/media/pets/*/image").permitAll()
                .requestMatchers("/api/payment/notify/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/payment/notify/**"))
            .addFilterAfter(new ActiveAccountFilter(db), SecurityContextHolderFilter.class)
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"请先登录\"}");
            }))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
            .build();
    }
}
