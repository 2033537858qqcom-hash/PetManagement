package com.petcare.grooming.inventory;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.store.AuditService;
import com.petcare.grooming.store.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class InventoryService {
    public record Item(long productId, int quantity) {}
    public record Rules(List<Item> items) {}
    public record Adjustment(String kind, Integer quantity, String reason, String requestId) {}
    public record Retail(Integer saleUnits, java.math.BigDecimal price) {}
    private final JdbcTemplate db;
    private final AccountAccess access;
    private final Clock clock;
    private final AuditService auditService;
    private final NotificationService notificationService;
    InventoryService(JdbcTemplate db, AccountAccess access, Clock clock, AuditService auditService, NotificationService notificationService) {
        this.db=db;
        this.access=access;
        this.clock=clock;
        this.auditService=auditService;
        this.notificationService=notificationService;
    }
    private void manager() { access.require(access.role("ADMIN") || access.role("MANAGER")); }
    private String operator() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null && auth.getName()!=null ? auth.getName() : "system";
    }
    private Map<String,Object> product(long id) {
        var rows=db.queryForList("select * from product where id=? for update",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"耗材不存在");
        return rows.get(0);
    }
    private void flow(long productId, int delta, int stock, String action, Long reference, String reason, String requestId) {
        db.update("insert into inventory_flow(product_id,delta,stock_after,action,reference_id,reason,operator_name,created_at,request_id) values (?,?,?,?,?,?,?,?,?)",productId,delta,stock,action,reference,reason,operator(),LocalDateTime.now(clock),requestId);
    }
    @Transactional public Map<String,Object> adjust(long id, Adjustment form) {
        manager();
        if (form.kind()==null || !List.of("IN","OUT","SET").contains(form.kind()) || form.quantity()==null || form.quantity()<0 || form.quantity()>1_000_000 || (!"SET".equals(form.kind()) && form.quantity()==0) || form.reason()==null || form.reason().isBlank() || form.reason().length()>255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写有效的库存操作、数量和原因");
        String request;
        try { request=java.util.UUID.fromString(form.requestId()).toString(); if(!request.equalsIgnoreCase(form.requestId())) throw new IllegalArgumentException(); }
        catch(Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请求编号无效"); }
        var product=product(id); int before=((Number)product.get("stock")).intValue();
        var previous=db.queryForList("select * from inventory_flow where request_id=?",request);
        if(!previous.isEmpty()) {
            var row=previous.get(0); int previousValue="SET".equals(form.kind())?((Number)row.get("stock_after")).intValue():Math.abs(((Number)row.get("delta")).intValue());
            if(((Number)row.get("product_id")).longValue()!=id || !form.kind().equals(row.get("action")) || !form.reason().trim().equals(row.get("reason")) || previousValue!=form.quantity()) throw new ResponseStatusException(HttpStatus.CONFLICT,"请求编号已用于其他库存操作");
            return product;
        }
        long after=switch(form.kind()) { case "IN" -> (long)before+form.quantity(); case "OUT" -> (long)before-form.quantity(); default -> form.quantity(); };
        if(after<0 || after>Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.CONFLICT,"库存数量不足或超出范围");
        db.update("update product set stock=? where id=?",after,id);
        try { flow(id,(int)after-before,(int)after,form.kind(),null,form.reason().trim(),request); }
        catch(org.springframework.dao.DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"请求编号已用于其他库存操作"); }
        auditService.log("INVENTORY_ADJUST", operator(), "PRODUCT", String.valueOf(id), form.kind() + " 数量: " + form.quantity() + ", 调整后库存: " + after + ", 原因: " + form.reason().trim());
        int threshold = ((Number) product.get("threshold")).intValue();
        if (after <= threshold) {
            notificationService.createNotification("STORE", null, "INVENTORY_ALERT", "耗材库存预警", "耗材 " + product.get("name") + " 当前库存 " + after + "，低于或等于预警阈值 " + threshold, "PROD_" + id, true);
        }
        return db.queryForMap("select * from product where id=?",id);
    }
    @Transactional public void configure(long serviceId, Rules rules) {
        manager();
        if (db.queryForList("select id from service where id=? for update",serviceId).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"服务项目不存在");
        if (rules.items()==null || rules.items().size()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"耗材规则无效");
        var seen=new java.util.HashSet<Long>();
        for(var item:rules.items()) {
            if(item==null || item.productId()<=0 || item.quantity()<=0 || item.quantity()>1_000_000 || !seen.add(item.productId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"耗材与数量无效或重复");
            if(db.queryForList("select id from product where id=?",item.productId()).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"耗材不存在");
        }
        db.update("delete from service_consumption where service_id=?",serviceId);
        for(var item:rules.items()) db.update("insert into service_consumption(service_id,product_id,quantity) values (?,?,?)",serviceId,item.productId(),item.quantity());
    }
    public List<Map<String,Object>> rules(long serviceId) { manager(); return db.queryForList("select r.*,p.name,p.unit from service_consumption r join product p on p.id=r.product_id where r.service_id=? order by r.product_id",serviceId); }
    public List<Map<String,Object>> history(long id) { manager(); return db.queryForList("select * from inventory_flow where product_id=? order by id desc",id); }
    @Transactional public Map<String,Object> retail(long id, Retail form) {
        manager(); product(id);
        if (form.saleUnits()==null || form.saleUnits()<1 || form.saleUnits()>1_000_000 || form.price()==null || form.price().scale()>2 || form.price().signum()<0 || form.price().compareTo(new java.math.BigDecimal("2000"))>0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入有效的商品包装数量和价格");
        db.update("update product set sale_units=?,price=? where id=?",form.saleUnits(),form.price(),id);
        return db.queryForMap("select * from product where id=?",id);
    }
    public void consumeCare(long serviceId, long groomingId) {
        var rules=db.queryForList("select product_id,quantity from service_consumption where service_id=? order by product_id",serviceId);
        for(var rule:rules) {
            long id=((Number)rule.get("product_id")).longValue(); int qty=((Number)rule.get("quantity")).intValue();
            var item=product(id); int stock=((Number)item.get("stock")).intValue();
            int after=Math.max(0,stock-qty);
            String reason=stock<qty?"洗护耗材扣减（库存不足差额 "+(qty-stock)+"）":"洗护耗材扣减";
            db.update("update product set stock=? where id=?",after,id);
            flow(id,after-stock,after,"CARE",groomingId,reason,null);
        }
    }
    public void recordSale(long productId, int quantity, long orderId) {
        int stock=db.queryForObject("select stock from product where id=?",Integer.class,productId);
        flow(productId,-quantity,stock,"SALE",orderId,"商品订单扣减",null);
    }
    public void restoreSale(long productId, int quantity, long orderId, String action) {
        var item=product(productId); int before=((Number)item.get("stock")).intValue();
        long after=(long)before+quantity;
        if(after>Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.CONFLICT,"退回后的库存超出范围");
        db.update("update product set stock=? where id=?",after,productId);
        flow(productId,quantity,(int)after,action,orderId,"商品订单退回",null);
    }
}
