package com.petcare.grooming.catalog;
import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.store.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ServicePricing {
    public record Item(String sizeType,BigDecimal price,Integer duration) {}
    public record Rules(List<Item> items) {}
    public record Quote(BigDecimal price,int duration) {}
    private final JdbcTemplate db;private final AccountAccess access;private final AuditService auditService;
    ServicePricing(JdbcTemplate db,AccountAccess access,AuditService auditService){this.db=db;this.access=access;this.auditService=auditService;}
    public List<Map<String,Object>> rules(long serviceId) {
        access.require(access.role("OWNER") || access.role("GROOMER") || access.role("RECEPTION") || access.role("MANAGER") || access.role("ADMIN"));
        return db.queryForList("select size_type,price,duration from service_price where service_id=? order by size_type",serviceId);
    }
    public Quote quote(Map<String,Object> service,String size) {
        var rules=db.queryForList("select price,duration from service_price where service_id=? and size_type=?",service.get("id"),size);
        if(!rules.isEmpty()) return new Quote((BigDecimal)rules.get(0).get("price"),((Number)rules.get(0).get("duration")).intValue());
        BigDecimal price=(BigDecimal)service.get("price");
        if("LARGE".equals(size)||"GIANT".equals(size)) price=price.add(new BigDecimal("50"));
        return new Quote(price,((Number)service.get("duration")).intValue());
    }
    @Transactional public List<Map<String,Object>> save(long id,Rules rules) {
        access.require(access.role("MANAGER") || access.role("ADMIN"));
        if(rules.items()==null || rules.items().size()>4) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"体型价格规则无效");
        var seen=new HashSet<String>();
        for(var item:rules.items()) {
            if(item==null || item.sizeType()==null || !List.of("SMALL","MEDIUM","LARGE","GIANT").contains(item.sizeType()) || !seen.add(item.sizeType()) || item.price()==null || item.price().scale()>2 || item.price().compareTo(BigDecimal.ONE)<0 || item.price().compareTo(new BigDecimal("2000"))>0 || item.duration()==null || item.duration()<1 || item.duration()>705) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写有效的体型、价格和时长");
        }
        db.queryForObject("select id from booking_lock where id=1 for update",Integer.class);
        if(db.queryForList("select id from service where id=? for update",id).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"服务项目不存在");
        db.update("delete from service_price where service_id=?",id);
        for(var item:rules.items()) db.update("insert into service_price(service_id,size_type,price,duration) values (?,?,?,?)",id,item.sizeType(),item.price(),item.duration());
        String operator = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.log("PRICE_UPDATE", operator, "SERVICE", String.valueOf(id), "更新体型价格项数: " + rules.items().size());
        return rules(id);
    }
}
