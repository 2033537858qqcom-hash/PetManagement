package com.petcare.grooming.account;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.security.AccountNames;
import com.petcare.grooming.security.PasswordPolicy;
import com.petcare.grooming.store.AuditService;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController @RequestMapping("/api/accounts")
public class AccountController {
    private final JdbcTemplate db;
    private final AccountAccess access;
    private final PasswordEncoder encoder;
    private final AuditService auditService;
    AccountController(JdbcTemplate db, AccountAccess access, PasswordEncoder encoder, AuditService auditService) {
        this.db=db;
        this.access=access;
        this.encoder=encoder;
        this.auditService=auditService;
    }
    public record NewAccount(String username, String password, String role, Long customerId, Long employeeId, String displayName, String title) {}
    public record Status(Boolean enabled) {}
    public record Password(String password) {}

    @GetMapping public ApiResponse<?> list() {
        access.require(access.role("ADMIN") || access.role("MANAGER"));
        if (access.role("MANAGER") && !access.role("ADMIN")) {
            return ApiResponse.ok(db.queryForList("select username,role_code,customer_id,employee_id,display_name,title,enabled from account where role_code<>'ADMIN' order by username"));
        }
        return ApiResponse.ok(db.queryForList("select username,role_code,customer_id,employee_id,display_name,title,enabled from account order by username"));
    }
    @PostMapping @Transactional public ApiResponse<?> create(@RequestBody NewAccount form) {
        access.require(access.role("ADMIN") || access.role("MANAGER"));
        String username = AccountNames.normalize(form.username());
        if (!AccountNames.valid(username) || !PasswordPolicy.valid(form.password()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"账号需为 3 至 32 位字母、数字或下划线，或有效的邮箱地址；密码至少 12 个字符且不超过 72 字节");
        if (form.role()==null || !List.of("ADMIN","MANAGER","RECEPTION","GROOMER","OWNER").contains(form.role())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择有效角色");
        if (access.role("MANAGER") && !access.role("ADMIN") && ("ADMIN".equals(form.role()) || "MANAGER".equals(form.role()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"店长仅可管理洗护师、前台与宠主账号");
        }
        String name=form.displayName(), title=form.title();
        Long customer=null, employee=null;
        if ("OWNER".equals(form.role())) {
            if (form.customerId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择绑定宠主");
            var rows=db.queryForList("select name,level_name from customer where id=?",form.customerId());
            if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"宠主不存在");
            if (db.queryForObject("select count(*) from account where customer_id=?", Integer.class, form.customerId()) > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"该宠主已绑定登录账号");
            customer=form.customerId(); name=rows.get(0).get("name").toString(); title=rows.get(0).get("level_name").toString()+"会员";
        } else if ("GROOMER".equals(form.role())) {
            if (form.employeeId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择绑定洗护师");
            var rows=db.queryForList("select name,title from employee where id=?",form.employeeId());
            if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"洗护师不存在");
            if (db.queryForObject("select count(*) from account where employee_id=?", Integer.class, form.employeeId()) > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"该洗护师已绑定登录账号");
            employee=form.employeeId(); name=rows.get(0).get("name").toString(); title=rows.get(0).get("title").toString();
        }
        if (name==null || name.isBlank() || name.length()>60 || title==null || title.isBlank() || title.length()>60) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写姓名和职位");
        try {
            db.update("insert into account(username,password_hash,role_code,customer_id,employee_id,display_name,title,enabled) values (?,?,?,?,?,?,?,1)", username,encoder.encode(form.password()),form.role(),customer,employee,name.trim(),title.trim());
        } catch (DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"账号已存在"); }
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.log("ACCOUNT_CREATE", operator, "ACCOUNT", username, "创建角色: " + form.role() + ", 姓名: " + name.trim());
        return ApiResponse.ok(db.queryForMap("select username,role_code,customer_id,employee_id,display_name,title,enabled from account where username=?",username));
    }
    @PutMapping("/{username}/status") @Transactional public ApiResponse<?> setStatus(@PathVariable String username, @RequestBody Status form) {
        access.require(access.role("ADMIN") || access.role("MANAGER"));
        if (form.enabled()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择启用状态");
        var admins=db.queryForList("select username from account where role_code='ADMIN' and enabled=1 for update");
        var target=db.queryForList("select username,role_code,enabled from account where username=?",username);
        if (target.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"账号不存在");
        String targetRole = target.get(0).get("role_code").toString();
        if (access.role("MANAGER") && !access.role("ADMIN") && ("ADMIN".equals(targetRole) || "MANAGER".equals(targetRole))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"店长仅可管理洗护师、前台与宠主账号");
        }
        if (!form.enabled() && username.equals(SecurityContextHolder.getContext().getAuthentication().getName())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不能停用当前账号");
        if (!form.enabled() && "ADMIN".equals(targetRole) && admins.size()<=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"至少保留一个管理员");
        db.update("update account set enabled=?, account_version=account_version+1 where username=?",form.enabled()?1:0,username);
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.log("ACCOUNT_STATUS", operator, "ACCOUNT", username, form.enabled() ? "启用账号" : "停用账号");
        return ApiResponse.ok(db.queryForMap("select username,role_code,customer_id,employee_id,display_name,title,enabled from account where username=?",username));
    }
    @PutMapping("/{username}/password") @Transactional public ApiResponse<?> resetPassword(@PathVariable String username, @RequestBody Password form) {
        access.require(access.role("ADMIN") || access.role("MANAGER"));
        if (!PasswordPolicy.valid(form.password())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"密码至少 12 个字符且不超过 72 字节");
        var target=db.queryForList("select role_code from account where username=?",username);
        if (target.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"账号不存在");
        String targetRole = target.get(0).get("role_code").toString();
        if (access.role("MANAGER") && !access.role("ADMIN") && ("ADMIN".equals(targetRole) || "MANAGER".equals(targetRole))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"店长仅可管理洗护师、前台与宠主账号");
        }
        int changed=db.update("update account set password_hash=?,account_version=account_version+1 where username=?",encoder.encode(form.password()),username);
        if (changed!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"账号不存在");
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.log("ACCOUNT_RESET_PASSWORD", operator, "ACCOUNT", username, "重置密码");
        return ApiResponse.ok(java.util.Map.of("username",username));
    }
    @DeleteMapping("/{username}") @Transactional public ApiResponse<?> deleteAccount(@PathVariable String username) {
        access.require(access.role("ADMIN") || access.role("MANAGER"));
        var target=db.queryForList("select username,role_code from account where username=?",username);
        if (target.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"账号不存在");
        String targetRole = target.get(0).get("role_code").toString();
        if (access.role("MANAGER") && !access.role("ADMIN") && ("ADMIN".equals(targetRole) || "MANAGER".equals(targetRole))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"店长仅可管理洗护师、前台与宠主账号");
        }
        if (username.equals(SecurityContextHolder.getContext().getAuthentication().getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不能删除当前登录账号");
        }
        if ("ADMIN".equals(targetRole)) {
            var admins = db.queryForList("select username from account where role_code='ADMIN' and enabled=1");
            if (admins.size() <= 1) throw new ResponseStatusException(HttpStatus.CONFLICT,"至少保留一个管理员账号");
        }
        db.update("delete from account where username=?",username);
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.log("ACCOUNT_DELETE", operator, "ACCOUNT", username, "删除账号");
        return ApiResponse.ok(java.util.Map.of("username",username));
    }
}
