package com.petcare.grooming.auth;

import com.petcare.grooming.security.AccountNames;
import com.petcare.grooming.security.PasswordPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Statement;

@Service
public class RegistrationService {
    public record Registration(String username, String password, String name, String phone) {}
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;

    public RegistrationService(JdbcTemplate db, PasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
    }

    @Transactional
    public String register(Registration form) {
        String username = AccountNames.normalize(form.username());
        String name = form.name() == null ? "" : form.name().trim();
        String phone = form.phone() == null ? "" : form.phone().trim();
        if (!AccountNames.valid(username))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号需为 3 至 32 位字母、数字或下划线，或有效的邮箱地址");
        if (!PasswordPolicy.valid(form.password()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少 12 个字符且不超过 72 字节");
        if (name.isBlank() || name.length() > 60)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写 1 至 60 个字符的姓名");
        if (!phone.matches("\\+?[0-9]{6,20}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的联系电话（6 至 20 位数字，可带国际区号）");
        if (db.queryForObject("select count(*) from account where username=?", Integer.class, username) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在，请更换账号");

        String passwordHash = encoder.encode(form.password());
        var existingCustomer = db.queryForList("select id, name, level_name from customer where phone=?", phone);
        long customerId;
        String levelTitle = "普通会员";
        String displayName = name;
        if (!existingCustomer.isEmpty()) {
            customerId = ((Number) existingCustomer.get(0).get("id")).longValue();
            if (db.queryForObject("select count(*) from account where customer_id=?", Integer.class, customerId) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "联系电话已登记，请使用已有账号登录，或联系门店开通账号");
            }
            Object lvl = existingCustomer.get(0).get("level_name");
            if (lvl != null && !lvl.toString().isBlank()) {
                String lvlStr = lvl.toString();
                levelTitle = lvlStr.endsWith("会员") ? lvlStr : lvlStr + "会员";
            }
            Object existingName = existingCustomer.get(0).get("name");
            if (existingName != null && !existingName.toString().isBlank()) {
                displayName = existingName.toString();
            } else {
                db.update("update customer set name=? where id=?", name, customerId);
            }
        } else {
            var keys = new GeneratedKeyHolder();
            try {
                db.update(connection -> {
                    var statement = connection.prepareStatement(
                        "insert into customer(name,phone,level_name,balance,points) values (?,?,'普通',0,0)",
                        Statement.RETURN_GENERATED_KEYS);
                    statement.setString(1, name);
                    statement.setString(2, phone);
                    return statement;
                }, keys);
                customerId = keys.getKey().longValue();
            } catch (DuplicateKeyException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "联系电话已登记，请使用已有账号登录，或联系门店开通账号");
            }
        }
        try {
            db.update("insert into account(username,password_hash,role_code,customer_id,display_name,title,enabled) values (?,?,'OWNER',?,?,?,1)",
                username, passwordHash, customerId, displayName, levelTitle);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在，请更换账号");
        }
        return username;
    }
}
