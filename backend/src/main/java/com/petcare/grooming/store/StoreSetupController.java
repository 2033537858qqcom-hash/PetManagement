package com.petcare.grooming.store;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.media.PhotoStorageService;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api")
public class StoreSetupController {
    private final JdbcTemplate db;
    private final AccountAccess access;
    private final PhotoStorageService photos;
    private final StoreConfigService storeConfig;
    private final AuditService auditService;
    StoreSetupController(JdbcTemplate db, AccountAccess access, PhotoStorageService photos, StoreConfigService storeConfig, AuditService auditService) {
        this.db = db;
        this.access = access;
        this.photos = photos;
        this.storeConfig = storeConfig;
        this.auditService = auditService;
    }
    public record StoreSettingsForm(String storeName, String businessHours, String openTime, String closeTime, Integer bufferMinutes, String address, String phone) {}
    public record CustomerForm(String name, String phone) {}
    public record EmployeeForm(String name,String title,String skills,String species,List<String> sizes,Boolean enabled) {}
    public record ResourceForm(String name,String species,Boolean enabled) {}
    public record ProductForm(String name,String unit,Integer threshold) {}

    private boolean textValid(String text,int max) { return text!=null && !text.isBlank() && text.trim().length()<=max; }
    private long insert(String sql,Object... values) {
        var keys=new GeneratedKeyHolder();
        db.update(connection -> {
            var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);
            return statement;
        },keys);
        return keys.getKey().longValue();
    }
    private Map<String,Object> get(String table,long id) {
        var found = db.queryForList("select * from "+table+" where id=?",id);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return found.get(0);
    }
    private boolean validSpecies(String species) {
        if (species == null) return false;
        String value = species.trim();
        if (value.equals("DOG") || value.equals("CAT") || value.equals("ALL") || value.equals("兔子") || value.equals("鸟类") || value.equals("仓鼠")) return true;
        return value.matches("^[\\u4e00-\\u9fa5A-Za-z0-9()（）\\-·\\s]{1,20}$");
    }
    private void manager() { access.require(access.role("MANAGER") || access.role("ADMIN")); }
    private List<Map<String,Object>> rows(String sql, Object... args) { return db.queryForList(sql, args); }
    private static boolean enabled(Map<String,Object> row) {
        Object value = row.get("enabled");
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.intValue() != 0;
        return value == null || !"0".equals(value.toString());
    }
    @GetMapping("/customers") public ApiResponse<?> customers() {
        if (access.role("OWNER")) return ApiResponse.ok(rows("select * from customer where id=?", access.customerId()));
        if (access.role("GROOMER")) return ApiResponse.ok(rows("select id, name, level_name from customer where id in (select customer_id from grooming where employee_id=? union select customer_id from appointment where employee_id=?) order by id", access.employeeId(), access.employeeId()));
        access.requireStaff();
        return ApiResponse.ok(rows("select * from customer order by id"));
    }
    @GetMapping("/employees") public ApiResponse<?> employees() {
        access.require(access.role("OWNER") || access.role("GROOMER") || access.role("RECEPTION") || access.role("MANAGER") || access.role("ADMIN"));
        if (access.role("OWNER")) {
            var list = rows("select id, name, title, species, sizes, enabled from employee where enabled<>0 order by id");
            list.forEach(emp -> emp.put("enabled", enabled(emp)));
            return ApiResponse.ok(list);
        }
        if (access.role("GROOMER")) {
            var list = rows("select * from employee where id=? order by id", access.employeeId());
            list.forEach(emp -> emp.put("enabled", enabled(emp)));
            return ApiResponse.ok(list);
        }
        var list = rows("select * from employee order by id");
        list.forEach(emp -> emp.put("enabled", enabled(emp)));
        return ApiResponse.ok(list);
    }
    @GetMapping("/resources") public ApiResponse<?> resources() {
        if (access.role("OWNER")) return ApiResponse.ok(List.of());
        var list = rows("select * from resource order by id");
        list.forEach(res -> res.put("enabled", enabled(res)));
        return ApiResponse.ok(list);
    }
    @PostMapping("/customers") public ApiResponse<?> customer(@RequestBody CustomerForm form) {
        access.requireStaff();
        if(!textValid(form.name(),60) || form.phone()==null || !form.phone().matches("\\+?[0-9]{6,20}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入姓名和有效的联系电话");
        try {
            long id=insert("insert into customer(name,phone,level_name,balance,points) values (?,?,?,0,0)",form.name().trim(),form.phone(),"普通");
            return ApiResponse.ok(get("customer",id));
        } catch(DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"联系电话已存在"); }
    }
    @PutMapping("/customers/{id}") @Transactional public ApiResponse<?> updateCustomer(@PathVariable long id, @RequestBody CustomerForm form) {
        access.requireStaff();
        get("customer", id);
        if(!textValid(form.name(),60) || form.phone()==null || !form.phone().matches("\\+?[0-9]{6,20}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入姓名和有效的联系电话");
        try {
            db.update("update customer set name=?, phone=? where id=?", form.name().trim(), form.phone(), id);
            db.update("update account set display_name=? where customer_id=?", form.name().trim(), id);
            return ApiResponse.ok(get("customer", id));
        } catch(DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"联系电话已存在"); }
    }
    @PostMapping("/employees") public ApiResponse<?> employee(@RequestBody EmployeeForm form) {
        manager();
        if(!textValid(form.name(),60) || !textValid(form.title(),60) || !textValid(form.skills(),200) || !validSpecies(form.species()) || form.sizes()==null || form.sizes().isEmpty() || form.sizes().size()>4 || form.sizes().stream().distinct().count()!=form.sizes().size() || !List.of("SMALL","MEDIUM","LARGE","GIANT").containsAll(form.sizes()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写有效的员工资料和适用宠物类型");
        long id=insert("insert into employee(name,title,skills,species,sizes,enabled) values (?,?,?,?,?,1)",form.name().trim(),form.title().trim(),form.skills().trim(),form.species(),String.join(",",form.sizes()));
        return ApiResponse.ok(get("employee",id));
    }
    @PutMapping("/employees/{id}") @Transactional public ApiResponse<?> updateEmployee(@PathVariable long id, @RequestBody EmployeeForm form) {
        manager();
        var existing = get("employee", id);
        if(!textValid(form.name(),60) || !textValid(form.title(),60) || !textValid(form.skills(),200) || !validSpecies(form.species()) || form.sizes()==null || form.sizes().isEmpty() || form.sizes().size()>4 || form.sizes().stream().distinct().count()!=form.sizes().size() || !List.of("SMALL","MEDIUM","LARGE","GIANT").containsAll(form.sizes()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写有效的员工资料和适用宠物类型");
        int enabledVal = form.enabled() != null ? (form.enabled() ? 1 : 0) : (existing.get("enabled") instanceof Number n ? n.intValue() : 1);
        db.update("update employee set name=?, title=?, skills=?, species=?, sizes=?, enabled=? where id=?",
            form.name().trim(), form.title().trim(), form.skills().trim(), form.species(), String.join(",", form.sizes()), enabledVal, id);
        db.update("update account set display_name=?, title=? where employee_id=?", form.name().trim(), form.title().trim(), id);
        return ApiResponse.ok(get("employee", id));
    }
    @PostMapping("/resources") public ApiResponse<?> resource(@RequestBody ResourceForm form) {
        manager();
        if(!textValid(form.name(),80) || !validSpecies(form.species())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写工位名称和适用宠物类型");
        long id=insert("insert into resource(name,species,enabled) values (?,?,1)",form.name().trim(),form.species());
        return ApiResponse.ok(get("resource",id));
    }
    @PutMapping("/resources/{id}") public ApiResponse<?> updateResource(@PathVariable long id, @RequestBody ResourceForm form) {
        manager();
        var existing = get("resource", id);
        if(!textValid(form.name(),80) || !validSpecies(form.species())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写工位名称和适用宠物类型");
        int enabledVal = form.enabled() != null ? (form.enabled() ? 1 : 0) : (existing.get("enabled") instanceof Number n ? n.intValue() : 1);
        db.update("update resource set name=?, species=?, enabled=? where id=?", form.name().trim(), form.species(), enabledVal, id);
        return ApiResponse.ok(get("resource", id));
    }
    @GetMapping("/store/settings")
    public ApiResponse<?> getStoreSettings() {
        return ApiResponse.ok(storeConfig.getSettings());
    }

    @PutMapping("/store/settings")
    public ApiResponse<?> updateStoreSettings(@RequestBody StoreSettingsForm form) {
        manager();
        if (!textValid(form.storeName(), 100)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "门店名称不能为空");
        if (form.openTime() == null || form.closeTime() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "营业时间不能为空");
        int buffer = form.bufferMinutes() != null ? form.bufferMinutes() : 15;
        String hours = (form.businessHours() != null && !form.businessHours().isBlank()) ? form.businessHours() : form.openTime() + " - " + form.closeTime();
        String addr = form.address() != null ? form.address() : "";
        String phone = form.phone() != null ? form.phone() : "";
        storeConfig.updateSettings(form.storeName().trim(), hours.trim(), form.openTime().trim(), form.closeTime().trim(), buffer, addr.trim(), phone.trim());
        String operator = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.log("STORE_SETTINGS_UPDATE", operator, "STORE", "1", "更新门店基础设置: " + form.storeName());
        return ApiResponse.ok(storeConfig.getSettings());
    }

    @PutMapping(value="/inventory/{id}", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<?> updateProduct(@PathVariable long id, @RequestBody ProductForm form) {
        manager();
        get("product", id);
        if(!textValid(form.name(),100) || !textValid(form.unit(),20) || form.threshold()==null || form.threshold()<0 || form.threshold()>1_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写耗材名称、单位和预警阈值");
        db.update("update product set name=?, unit=?, threshold=? where id=?", form.name().trim(), form.unit().trim(), form.threshold(), id);
        return ApiResponse.ok(get("product", id));
    }

    @PutMapping(value="/inventory/{id}", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ApiResponse<?> updateProductWithImage(@PathVariable long id, @RequestPart("product") ProductForm form, @RequestPart(value="image", required=false) MultipartFile image) {
        manager();
        var existing = get("product", id);
        if(!textValid(form.name(),100) || !textValid(form.unit(),20) || form.threshold()==null || form.threshold()<0 || form.threshold()>1_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写耗材名称、单位和预警阈值");
        String imageKey = (String) existing.get("image_key");
        if (image != null && !image.isEmpty()) {
            imageKey = photos.saveProductImage(image);
        }
        db.update("update product set name=?, unit=?, threshold=?, image_key=? where id=?", form.name().trim(), form.unit().trim(), form.threshold(), imageKey, id);
        return ApiResponse.ok(get("product", id));
    }
    @PostMapping(value="/inventory", consumes=MediaType.APPLICATION_JSON_VALUE) public ApiResponse<?> product(@RequestBody ProductForm form) {
        return createProduct(form, null);
    }
    @PostMapping(value="/inventory", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional
    public ApiResponse<?> productWithImage(@RequestPart("product") ProductForm form, @RequestPart(value="image", required=false) MultipartFile image) {
        return createProduct(form, image);
    }
    private ApiResponse<?> createProduct(ProductForm form, MultipartFile image) {
        manager();
        if(!textValid(form.name(),100) || !textValid(form.unit(),20) || form.threshold()==null || form.threshold()<0 || form.threshold()>1_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写耗材名称、单位和预警阈值");
        String imageKey = image == null ? null : photos.saveProductImage(image);
        if (imageKey != null) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) photos.discardProductImage(imageKey);
            }
        });
        long id=insert("insert into product(name,unit,threshold,stock,price,sale_units,image_key) values (?,?,?,0,0,1,?)",form.name().trim(),form.unit().trim(),form.threshold(),imageKey);
        return ApiResponse.ok(get("product",id));
    }
}
