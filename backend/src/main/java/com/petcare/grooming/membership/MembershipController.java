package com.petcare.grooming.membership;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.store.AuditService;
import com.petcare.grooming.store.NotificationService;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/api")
public class MembershipController {
    public record Recharge(BigDecimal amount,String requestId) {}
    public record CardSale(String name,Integer total,BigDecimal price,List<Long> serviceIds,String requestId) {}
    private final JdbcTemplate db; private final AccountAccess access; private final Clock clock;
    private final AuditService auditService; private final NotificationService notificationService;
    MembershipController(JdbcTemplate db, AccountAccess access, Clock clock, AuditService auditService, NotificationService notificationService) {
        this.db = db;
        this.access = access;
        this.clock = clock;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }
    private String requestId(String value) {
        try { String canonical=UUID.fromString(value).toString(); if (!canonical.equalsIgnoreCase(value)) throw new IllegalArgumentException(); return canonical; }
        catch(Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请求编号无效"); }
    }
    private void money(BigDecimal value) { if(value==null || value.scale()>2 || value.signum()<=0 || value.compareTo(new BigDecimal("9999999.99"))>0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入有效的金额，最多两位小数"); }
    private void lock() { db.queryForObject("select id from member_lock where id=1 for update",Integer.class); }
    private String operator() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private Map<String,Object> customer(long id) {
        var rows=db.queryForList("select * from customer where id=? for update",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"宠主不存在");
        return rows.get(0);
    }
    private Map<String,Object> card(long id) { return db.queryForMap("select c.*,u.name customer_name from prepaid_card c join customer u on u.id=c.customer_id where c.id=?",id); }

    @GetMapping("/members/cards") public ApiResponse<?> cards() {
        String sql="select c.*, u.name customer_name from prepaid_card c join customer u on u.id=c.customer_id";
        List<Map<String, Object>> list;
        if (access.role("OWNER")) list = db.queryForList(sql+" where c.customer_id=? order by c.id", access.customerId());
        else if (access.role("GROOMER")) return ApiResponse.ok(List.of());
        else { access.requireStaff(); list = db.queryForList(sql+" order by c.id"); }
        for (var card : list) {
            var serviceIds = db.queryForList("select service_id from prepaid_card_service where card_id=?", card.get("id"))
                .stream().map(r -> ((Number) r.get("service_id")).longValue()).toList();
            card.put("service_ids", serviceIds);
        }
        return ApiResponse.ok(list);
    }
    @PostMapping("/customers/{id}/recharge") @Transactional public ApiResponse<?> recharge(@PathVariable long id,@RequestBody Recharge form) {
        access.requireStaff(); money(form.amount()); String request=requestId(form.requestId()); lock();
        var old=db.queryForList("select * from member_recharge where request_id=?",request);
        if(!old.isEmpty()) {
            if(((Number)old.get(0).get("customer_id")).longValue()!=id || ((BigDecimal)old.get(0).get("amount")).compareTo(form.amount())!=0) throw new ResponseStatusException(HttpStatus.CONFLICT,"请求编号已用于其他充值");
            return ApiResponse.ok(old.get(0));
        }
        var customer=customer(id); BigDecimal balance=(BigDecimal)customer.get("balance");
        if(balance.add(form.amount()).compareTo(new BigDecimal("99999999.99"))>0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"充值后余额超出范围");
        var now=LocalDateTime.now(clock); String operator=operator();
        db.update("update customer set balance=balance+? where id=?",form.amount(),id);
        db.update("insert into balance_flow(customer_id,grooming_id,amount,operator_name,created_at) values (?,NULL,?,?,?)",id,form.amount(),operator,now);
        db.update("insert into member_recharge(request_id,customer_id,amount,operator_name,created_at) values (?,?,?,?,?)",request,id,form.amount(),operator,now);
        auditService.log("MEMBER_RECHARGE", operator, "CUSTOMER", String.valueOf(id), "会员充值，金额: ￥" + form.amount());
        notificationService.createNotification("OWNER", id, "RECHARGE_SUCCESS", "会员充值成功", "您的会员账户已成功充值 ￥" + form.amount(), request, false);
        return ApiResponse.ok(db.queryForMap("select * from member_recharge where request_id=?",request));
    }

    @PostMapping("/customers/{id}/cards") @Transactional public ApiResponse<?> sellCard(@PathVariable long id,@RequestBody CardSale form) {
        access.requireStaff(); money(form.price()); String request=requestId(form.requestId());
        if(form.name()==null || form.name().isBlank() || form.name().trim().length()>100 || form.total()==null || form.total()<1 || form.total()>1000 || form.serviceIds()==null || form.serviceIds().isEmpty() || form.serviceIds().size()>100 || form.serviceIds().contains(null) || new HashSet<>(form.serviceIds()).size()!=form.serviceIds().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写次卡名称、次数和适用服务");
        lock(); var old=db.queryForList("select * from prepaid_card_sale where request_id=?",request);
        if(!old.isEmpty()) {
            long oldCard=((Number)old.get(0).get("card_id")).longValue(); var existing=card(oldCard);
            var services=db.queryForList("select service_id from prepaid_card_service where card_id=?",Long.class,oldCard);
            if(((Number)old.get(0).get("customer_id")).longValue()!=id || ((BigDecimal)old.get(0).get("amount")).compareTo(form.price())!=0 || !existing.get("name").equals(form.name().trim()) || ((Number)existing.get("total")).intValue()!=form.total() || !new HashSet<>(services).equals(new HashSet<>(form.serviceIds())))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"请求编号已用于其他次卡办理");
            return ApiResponse.ok(existing);
        }
        customer(id);
        for(long service:form.serviceIds()) if(db.queryForList("select id from service where id=? and enabled<>0",service).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"包含不可用的服务项目");
        var keys=new GeneratedKeyHolder();
        db.update(connection -> { var statement=connection.prepareStatement("insert into prepaid_card(customer_id,name,remaining,total) values (?,?,?,?)",Statement.RETURN_GENERATED_KEYS); statement.setLong(1,id); statement.setString(2,form.name().trim()); statement.setInt(3,form.total()); statement.setInt(4,form.total()); return statement; },keys);
        long cardId=keys.getKey().longValue();
        for(long service:form.serviceIds()) db.update("insert into prepaid_card_service(card_id,service_id) values (?,?)",cardId,service);
        db.update("insert into prepaid_card_sale(request_id,card_id,customer_id,amount,operator_name,created_at) values (?,?,?,?,?,?)",request,cardId,id,form.price(),operator(),LocalDateTime.now(clock));
        auditService.log("CARD_SALE", operator(), "CUSTOMER", String.valueOf(id), "办理次卡: " + form.name().trim() + " " + form.total() + "次，金额: ￥" + form.price());
        notificationService.createNotification("OWNER", id, "CARD_PURCHASE", "次卡开通成功", "已为您开通「" + form.name().trim() + "」(" + form.total() + "次)", request, false);
        return ApiResponse.ok(card(cardId));
    }

    @GetMapping("/customers/{id}/history") public ApiResponse<?> history(@PathVariable long id) {
        if(access.role("OWNER")) access.require(id==access.customerId()); else access.requireStaff();
        var result=new LinkedHashMap<String,Object>();
        result.put("balance",db.queryForList("select * from balance_flow where customer_id=? order by id desc",id));
        result.put("cardSales",db.queryForList("select * from prepaid_card_sale where customer_id=? order by created_at desc",id));
        result.put("cardUsage",db.queryForList("select f.* from prepaid_card_flow f join prepaid_card c on c.id=f.card_id where c.customer_id=? order by f.id desc",id));
        return ApiResponse.ok(result);
    }
}
