package com.petcare.grooming.auth;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.security.AccountNames;
import com.petcare.grooming.security.PasswordPolicy;
import com.petcare.grooming.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AccountAccess access;
    private final JdbcTemplate db;
    private final RegistrationService registration;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;
    private final HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
    public AuthController(AuthenticationManager authenticationManager, AccountAccess access, JdbcTemplate db, RegistrationService registration, org.springframework.security.crypto.password.PasswordEncoder encoder) { this.authenticationManager = authenticationManager; this.access = access; this.db = db; this.registration = registration; this.encoder = encoder; }
    record Credentials(@NotBlank String username, @NotBlank String password) {}
    record PasswordChange(String oldPassword, String newPassword) {}
    record AccountProfile(String username, String role, String name, String title, Long customerId, Long employeeId) {}
    private AccountProfile profile(Authentication auth) {
        String authority = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).findFirst().orElse("");
        return switch (authority) {
            case "ROLE_RECEPTION" -> staffProfile(auth, "前台");
            case "ROLE_GROOMER" -> {
                long id = access.employeeId();
                var employee = db.queryForMap("select name,title from employee where id=?", id);
                yield new AccountProfile(auth.getName(), "洗护师", employee.get("name").toString(), employee.get("title").toString(), null, id);
            }
            case "ROLE_OWNER" -> {
                long id = access.customerId();
                var customer = db.queryForMap("select name,level_name from customer where id=?", id);
                yield new AccountProfile(auth.getName(), "宠主", customer.get("name").toString(), customer.get("level_name").toString() + "会员", id, null);
            }
            case "ROLE_ADMIN" -> staffProfile(auth, "管理员");
            case "ROLE_MANAGER" -> staffProfile(auth, "店长");
            default -> throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "无效的账号角色");
        };
    }
    private AccountProfile staffProfile(Authentication auth, String role) {
        var row = db.queryForMap("select display_name,title from account where username=?", auth.getName());
        return new AccountProfile(auth.getName(), role, row.get("display_name").toString(), row.get("title").toString(), null, null);
    }
    @GetMapping("/csrf") ApiResponse<Map<String, String>> csrf(CsrfToken token) { return ApiResponse.ok(Map.of("token", token.getToken(), "header", token.getHeaderName())); }
    @GetMapping("/me") ApiResponse<AccountProfile> me(Authentication auth) { return ApiResponse.ok(profile(auth)); }
    @PutMapping("/password") @org.springframework.transaction.annotation.Transactional
    ApiResponse<?> changePassword(@RequestBody PasswordChange form, Authentication auth, HttpServletRequest request) {
        if (auth == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "请先登录");
        if (form.oldPassword() == null || form.oldPassword().isBlank() || !PasswordPolicy.valid(form.newPassword())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "请输入原密码，且新密码至少 12 个字符且不超过 72 字节");
        }
        var rows = db.queryForList("select password_hash from account where username=? for update", auth.getName());
        if (rows.isEmpty() || rows.get(0).get("password_hash") == null || !encoder.matches(form.oldPassword(), rows.get(0).get("password_hash").toString())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "原密码不正确");
        }
        db.update("update account set password_hash=?, account_version=account_version+1 where username=?", encoder.encode(form.newPassword()), auth.getName());
        Long nextVersion = db.queryForObject("select account_version from account where username=?", Long.class, auth.getName());
        var session = request.getSession(false);
        if (session != null) session.setAttribute("account_version", nextVersion);
        return ApiResponse.ok(Map.of("username", auth.getName()));
    }
    @PostMapping("/register") ResponseEntity<?> register(@RequestBody RegistrationService.Registration form, HttpServletRequest request, HttpServletResponse response) {
        String username = registration.register(form);
        return login(new Credentials(username, form.password()), request, response);
    }
    @PostMapping("/login") ResponseEntity<?> login(@Valid @RequestBody Credentials credentials, HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(AccountNames.normalize(credentials.username()), credentials.password()));
            if (request.getSession(false) != null) request.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth); SecurityContextHolder.setContext(context);
            repository.saveContext(context, request, response);
            request.getSession().setAttribute("account_version", db.queryForObject("select account_version from account where username=?", Long.class, auth.getName()));
            return ResponseEntity.ok(ApiResponse.ok(profile(auth)));
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(401).body(ApiResponse.error(401, "账号或密码错误"));
        }
    }
}
