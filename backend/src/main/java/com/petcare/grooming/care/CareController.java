package com.petcare.grooming.care;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.booking.SchedulingRules;
import com.petcare.grooming.catalog.ServicePricing;
import com.petcare.grooming.inventory.InventoryService;
import com.petcare.grooming.media.PhotoStorageService;
import com.petcare.grooming.store.StoreConfigService;
import com.petcare.grooming.store.AuditService;
import com.petcare.grooming.store.NotificationService;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.sql.Statement;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api")
public class CareController {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final JdbcTemplate db;
    private final Clock clock;
    private final AccountAccess access;
    private final InventoryService inventory;
    private final ServicePricing pricing;
    private final StoreConfigService storeConfig;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final PhotoStorageService photos;

    CareController(JdbcTemplate db, Clock clock, AccountAccess access, InventoryService inventory, ServicePricing pricing, StoreConfigService storeConfig, AuditService auditService, NotificationService notificationService, PhotoStorageService photos) {
        this.db = db;
        this.clock = clock;
        this.access = access;
        this.inventory = inventory;
        this.pricing = pricing;
        this.storeConfig = storeConfig;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.photos = photos;
    }

    public record PetForm(Long customerId, String name, String species, String breed, String ageBand, String sizeType, BigDecimal weight, String coat, String allergy, String skinHistory, String groomingHistory) {}
    public record ServiceForm(String name, String category, BigDecimal price, Integer duration, String species) {}
    public record ServiceUpdate(String name, String category, BigDecimal price, Integer duration, String species, Boolean enabled) {}
    public record WalkIn(long petId, long serviceId, long employeeId, Long resourceId) {}
    public record Assessment(BigDecimal weight, String skin, String coat, String ear, String groomerNote) {}
    public record HealthEntryForm(BigDecimal weight, String note, String skinCode, String coatCode, String earCode, Integer score) {}
    public record AdvanceForm(String precheckNote) {}
    public record ReassignForm(Long employeeId) {}
    public record OrderForm(Long customerId, long productId, int quantity, String requestId) {}
    public record RetailSettle(String method) {}
    public record Handover(String pickupCode) {}
    public record Settlement(String method, Long cardId, String addonMethod, String tradeNo) {
        public Settlement(String method, Long cardId, String addonMethod) { this(method, cardId, addonMethod, null); }
    }
    public record Refund(String reason) {}
    public record VoidForm(String reason) {}
    public record Decision(String decision) {}
    public record AddonForm(String name, String reason, BigDecimal amount, String requestId) {}
    public record ReviewForm(Long groomingId, Integer rating, String content) {}

    @GetMapping("/pets") public ApiResponse<?> pets(@RequestParam(required=false) Long customerId) {
        List<Map<String,Object>> list;
        if (role("OWNER")) { if (customerId != null) access.require(customerId == access.customerId()); list = rows("select * from pet where customer_id=? order by id", access.customerId()); }
        else if (role("GROOMER")) { list = rows("select * from pet where id in (select pet_id from grooming where employee_id=? union select pet_id from appointment where employee_id=?) order by id", access.employeeId(), access.employeeId()); }
        else { access.requireStaff(); list = customerId == null ? rows("select * from pet order by id") : rows("select * from pet where customer_id=? order by id", customerId); }
        list.forEach(CareTerms::decorate);
        list.forEach(this::attachPhoto);
        return ApiResponse.ok(list);
    }
    @GetMapping("/services") public ApiResponse<?> services() {
        access.require(role("OWNER") || role("GROOMER") || role("RECEPTION") || role("MANAGER") || role("ADMIN"));
        var list = role("OWNER") ? rows("select * from service where enabled<>0 order by id") : rows("select * from service order by id");
        list.forEach(this::presentService);
        return ApiResponse.ok(list);
    }
    @GetMapping("/services/recommendations/{petId}") public ApiResponse<?> recommendations(@PathVariable long petId) {
        access.requirePet(petId);
        var pet = one("select * from pet where id=?", petId);
        var list = rows("select * from service where enabled<>0 and (species='ALL' or species=?)", pet.get("species"));
        for (var service : list) {
            int score = 75;
            String reason = "适合该宠物的基础护理";
            if ("CAT".equals(pet.get("species")) && "CAT".equals(service.get("species"))) { score = 96; reason = "猫咪适用，安静环境有助于降低应激"; }
            else if (pet.get("coat").toString().contains("毛") && "深层护理".equals(service.get("category"))) { score = 91; reason = "根据毛发类型推荐深层护理"; }
            service.put("score", score); service.put("reason", reason);
        }
        list.sort((a, b) -> Integer.compare((int) b.get("score"), (int) a.get("score")));
        list.forEach(this::presentService);
        return ApiResponse.ok(list);
    }
    @GetMapping("/grooming") public ApiResponse<?> groomingList() {
        String sql = "select g.*, p.name pet_name, e.name employee_name, s.name service_name from grooming g join pet p on p.id=g.pet_id join employee e on e.id=g.employee_id join service s on s.id=g.service_id";
        if (role("OWNER")) return ApiResponse.ok(rows(sql + " where g.customer_id=? order by g.id", access.customerId()));
        if (role("GROOMER")) return ApiResponse.ok(rows(sql + " where g.employee_id=? order by g.id", access.employeeId()));
        access.requireStaff();
        return ApiResponse.ok(rows(sql + " order by g.id"));
    }
    @GetMapping("/grooming/addons") public ApiResponse<?> addons() {
        String sql = "select a.*, p.name pet_name from addon a join grooming g on g.id=a.grooming_id join pet p on p.id=g.pet_id";
        if (role("OWNER")) return ApiResponse.ok(rows(sql + " where g.customer_id=? order by a.id", access.customerId()));
        if (role("GROOMER")) return ApiResponse.ok(rows(sql + " where g.employee_id=? order by a.id", access.employeeId()));
        access.requireStaff();
        return ApiResponse.ok(rows(sql + " order by a.id"));
    }
    @PostMapping("/grooming/{id}/addons") @Transactional public ApiResponse<?> requestAddon(@PathVariable long id, @RequestBody AddonForm form) {
        access.require(role("GROOMER") || role("MANAGER") || role("ADMIN"));
        var order = one("select * from grooming where id=? for update", id);
        if (role("GROOMER")) access.require(((Number) order.get("employee_id")).longValue() == access.employeeId());
        if (form.name() == null || form.name().isBlank() || form.name().trim().length() > 100 || form.reason() == null || form.reason().isBlank() || form.reason().trim().length() > 300 || form.amount() == null || form.amount().scale() > 2 || form.amount().signum() <= 0 || form.amount().compareTo(new BigDecimal("2000")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写有效的加项名称、原因和费用");
        String request;
        try { request = UUID.fromString(form.requestId()).toString(); if (!request.equalsIgnoreCase(form.requestId())) throw new IllegalArgumentException(); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求编号无效"); }
        var existing = rows("select * from addon where request_id=?", request);
        if (!existing.isEmpty()) {
            var row = existing.get(0);
            if (((Number) row.get("grooming_id")).longValue() != id || !row.get("name").equals(form.name().trim()) || !row.get("reason").equals(form.reason().trim()) || ((BigDecimal) row.get("amount")).compareTo(form.amount()) != 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "请求编号已用于其他加项");
            return ApiResponse.ok(row);
        }
        if (!"GROOMING".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "只有护理中的工单可以申请加项");
        var keys = new GeneratedKeyHolder();
        try {
            db.update(connection -> {
                var statement = connection.prepareStatement("insert into addon(grooming_id,name,reason,amount,status,request_id) values (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, id); statement.setString(2, form.name().trim()); statement.setString(3, form.reason().trim()); statement.setBigDecimal(4, form.amount()); statement.setString(5, "PENDING"); statement.setString(6, request);
                return statement;
            }, keys);
        } catch (org.springframework.dao.DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "请求编号已用于其他加项"); }
        var newAddon = one("select * from addon where id=?", keys.getKey().longValue());
        var pet = one("select name from pet where id=?", order.get("pet_id"));
        notificationService.createNotification("OWNER", ((Number) order.get("customer_id")).longValue(), "ADDON_CONFIRM", "加项待确认", pet.get("name") + " 护理中有加项申请: " + form.name().trim() + "，金额 ¥" + form.amount(), String.valueOf(id), true);
        return ApiResponse.ok(newAddon);
    }
    @PostMapping("/grooming/addons/{id}/decision") @Transactional public ApiResponse<?> decide(@PathVariable long id, @RequestBody Decision decision) {
        var addon = one("select g.customer_id,g.employee_id,g.id grooming_id from addon a join grooming g on g.id=a.grooming_id where a.id=?", id);
        if (role("OWNER")) access.require(((Number) addon.get("customer_id")).longValue() == access.customerId());
        else if (role("GROOMER")) access.require("REJECTED".equals(decision.decision()) && ((Number) addon.get("employee_id")).longValue() == access.employeeId());
        else access.requireStaff();
        one("select id from grooming where id=? for update", addon.get("grooming_id"));
        if (decision.decision() == null || !List.of("APPROVED", "REJECTED").contains(decision.decision())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的确认结果");
        if ("APPROVED".equals(decision.decision())) {
            var g = one("select status from grooming where id=?", addon.get("grooming_id"));
            String gStatus = (String) g.get("status");
            if ("COMPLETED".equals(gStatus) || "CANCELLED".equals(gStatus)) throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已结束，无法变更加项");
            var settlements = rows("select status from service_settlement where grooming_id=?", addon.get("grooming_id"));
            if (!settlements.isEmpty() && "PAID".equals(settlements.get(0).get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已完成结算，无法批准新加项");
        }
        int changed = db.update("update addon set status=? where id=? and status='PENDING'", decision.decision(), id);
        if (changed == 0) { one("select id from addon where id=?", id); throw new ResponseStatusException(HttpStatus.CONFLICT, "加项已处理"); }
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        db.update("insert into grooming_event(grooming_id,event_type,operator_name,created_at) values (?,?,?,?)",
            addon.get("grooming_id"), "APPROVED".equals(decision.decision()) ? "ADDON_APPROVED" : "ADDON_REJECTED", operator, java.time.LocalDateTime.now(clock));
        return ApiResponse.ok(one("select * from addon where id=?", id));
    }
    @GetMapping("/reviews") public ApiResponse<?> reviews() {
        if (role("GROOMER")) return ApiResponse.ok(rows("select * from review where grooming_id in (select id from grooming where employee_id=?) order by id desc", access.employeeId()));
        access.require(role("OWNER") || role("MANAGER") || role("ADMIN") || role("RECEPTION"));
        if (role("OWNER")) return ApiResponse.ok(rows("select * from review where grooming_id in (select id from grooming where customer_id=?) order by id desc", access.customerId()));
        return ApiResponse.ok(rows("select * from review order by id desc"));
    }
    @PostMapping("/reviews") @Transactional public ApiResponse<?> createReview(@RequestBody ReviewForm form) {
        access.require(role("OWNER"));
        if (form.groomingId() == null || form.rating() == null || form.rating() < 1 || form.rating() > 5 || form.content() == null || form.content().isBlank() || form.content().trim().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 1 到 5 星评价并填写不超过 500 字的评价内容");
        var order = one("select g.*, p.name pet_name, c.name customer_name from grooming g join pet p on p.id=g.pet_id join customer c on c.id=g.customer_id where g.id=? for update", form.groomingId());
        access.require(((Number) order.get("customer_id")).longValue() == access.customerId());
        if (!"COMPLETED".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "完成交宠后才能评价本次护理");
        if (!rows("select id from review where grooming_id=?", form.groomingId()).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "该工单已评价");
        var keys = new GeneratedKeyHolder();
        db.update(connection -> {
            var st = connection.prepareStatement("insert into review(customer_name, pet_name, rating, content, grooming_id) values (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            st.setString(1, order.get("customer_name").toString());
            st.setString(2, order.get("pet_name").toString());
            st.setInt(3, form.rating());
            st.setString(4, form.content().trim());
            st.setLong(5, form.groomingId());
            return st;
        }, keys);
        return ApiResponse.ok(one("select * from review where id=?", keys.getKey().longValue()));
    }

    @PostMapping("/appointments/{id}/check-in")
    @Transactional
    public ApiResponse<?> checkIn(@PathVariable long id) {
        require(role("MANAGER") || role("ADMIN") || role("RECEPTION"));
        var appointment = one("select * from appointment where id=? for update", id);
        if (!"CONFIRMED".equals(appointment.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "预约已取消或已签到");
        if (!LocalDate.now(clock).equals(((java.sql.Date) appointment.get("appointment_date")).toLocalDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能签到当天的预约");
        var keys = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement("insert into grooming(pet_id,customer_id,employee_id,service_id,stage,status,pickup_code,appointment_id,resource_id,base_price) values (?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, appointment.get("pet_id")); statement.setObject(2, appointment.get("customer_id"));
            statement.setObject(3, appointment.get("employee_id")); statement.setObject(4, appointment.get("service_id"));
            statement.setString(5, "CHECK_IN"); statement.setString(6, "GROOMING"); statement.setString(7, pickupCode());
            statement.setLong(8, id); statement.setObject(9, appointment.get("resource_id"));
            statement.setObject(10,appointment.get("price"));
            return statement;
        }, keys);
        long groomingId = keys.getKey().longValue();
        db.update("update appointment set status='IN_SERVICE' where id=?", id);
        recordEvent(groomingId, "CHECK_IN");
        return ApiResponse.ok(grooming(groomingId));
    }

    @PostMapping("/grooming/{id}/advance")
    @Transactional
    public ApiResponse<?> advance(@PathVariable long id, @RequestBody(required = false) AdvanceForm form) {
        require(role("GROOMER") || role("MANAGER") || role("ADMIN"));
        var order = one("select * from grooming where id=? for update", id);
        if (role("GROOMER")) require(((Number) order.get("employee_id")).longValue() == access.employeeId());
        if (!"GROOMING".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "工单不在护理中");
        String currentStage = text(order.get("stage"));
        String next = switch (currentStage) {
            case "CHECK_IN" -> "BATHING"; case "BATHING" -> "DRYING"; case "DRYING" -> "TRIMMING";
            default -> throw new ResponseStatusException(HttpStatus.CONFLICT, "当前阶段不能继续推进");
        };
        if ("CHECK_IN".equals(currentStage) && form != null && form.precheckNote() != null && !form.precheckNote().isBlank()) {
            String note = form.precheckNote().trim();
            if (note.length() > 300) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "接宠检查备注不超过 300 字");
            db.update("update grooming set stage=?, precheck_note=? where id=?", next, note, id);
        } else {
            db.update("update grooming set stage=? where id=?", next, id);
        }
        recordEvent(id, next);
        return ApiResponse.ok(grooming(id));
    }

    @PostMapping("/grooming/{id}/rollback")
    @Transactional
    public ApiResponse<?> rollbackStage(@PathVariable long id) {
        require(role("GROOMER") || role("MANAGER") || role("ADMIN"));
        var order = one("select * from grooming where id=? for update", id);
        if (role("GROOMER")) require(((Number) order.get("employee_id")).longValue() == access.employeeId());
        if (!"GROOMING".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "工单不在护理中");
        String prev = switch (text(order.get("stage"))) {
            case "BATHING" -> "CHECK_IN"; case "DRYING" -> "BATHING"; case "TRIMMING" -> "DRYING";
            default -> throw new ResponseStatusException(HttpStatus.CONFLICT, "当前阶段不能回退");
        };
        db.update("update grooming set stage=? where id=?", prev, id);
        recordEvent(id, "ROLLBACK_" + prev);
        return ApiResponse.ok(grooming(id));
    }

    @PutMapping("/grooming/{id}/reassign")
    @Transactional
    public ApiResponse<?> reassign(@PathVariable long id, @RequestBody ReassignForm form) {
        require(role("MANAGER") || role("ADMIN"));
        if (form.employeeId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择接手洗护师");
        db.queryForObject("select id from booking_lock where id=1 for update", Integer.class);
        var order = one("select * from grooming where id=? for update", id);
        if (!"GROOMING".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "只有护理中的工单可以改派洗护师");
        var shifts = rows("select * from employee_schedule where schedule_date=? and employee_id=?", LocalDate.now(clock), form.employeeId());
        if (!shifts.isEmpty() && "OFF".equals(shifts.get(0).get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该洗护师今日休假，无法接单");
        }
        var employee = one("select * from employee where id=?", form.employeeId());
        if (!enabled(employee)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该洗护师已停用");
        var pet = one("select * from pet where id=?", order.get("pet_id"));
        if (!pet.get("species").toString().equals(employee.get("species").toString()) || !employee.get("sizes").toString().contains(pet.get("size_type").toString())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这位洗护师不负责该类型宠物");
        }
        db.update("update grooming set employee_id=? where id=?", form.employeeId(), id);
        if (order.get("appointment_id") != null) {
            db.update("update appointment set employee_id=? where id=?", form.employeeId(), order.get("appointment_id"));
        }
        recordEvent(id, "REASSIGN");
        return ApiResponse.ok(grooming(id));
    }

    @PostMapping("/grooming/{id}/void")
    @Transactional
    public ApiResponse<?> voidGrooming(@PathVariable long id, @RequestBody(required = false) VoidForm form) {
        require(role("MANAGER") || role("ADMIN") || role("RECEPTION"));
        var order = one("select * from grooming where id=? for update", id);
        if (!"GROOMING".equals(order.get("status")) && !"WAITING_PICKUP".equals(order.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有进行中或待接宠的工单可以作废终止");
        }
        var paidSettlement = rows("select grooming_id from service_settlement where grooming_id=? and status='PAID'", id);
        if (!paidSettlement.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已完成收费的工单请先办理退款后再作废");
        }
        db.update("update grooming set status='CANCELLED' where id=?", id);
        if (order.get("appointment_id") != null) {
            db.update("update appointment set status='CANCELLED' where id=?", order.get("appointment_id"));
        }
        recordEvent(id, "CANCELLED");
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.log("GROOMING_VOID", operator, "GROOMING", String.valueOf(id), form != null && form.reason() != null ? form.reason().trim() : "工单作废终止");
        return ApiResponse.ok(grooming(id));
    }

    @PostMapping("/grooming/{id}/handover")
    @Transactional
    public ApiResponse<?> handover(@PathVariable long id, @RequestBody Handover form) {
        require(role("MANAGER") || role("ADMIN") || role("RECEPTION"));
        var order = one("select * from grooming where id=? for update", id);
        if (!"WAITING_PICKUP".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "工单不在待接宠状态");
        if (form.pickupCode() == null || !form.pickupCode().equals(order.get("pickup_code"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "接宠凭证不正确");
        if (rows("select grooming_id from service_settlement where grooming_id=? and (status='PAID' or status='REFUNDED')", id).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先完成结算");
        db.update("update grooming set status='COMPLETED' where id=?", id);
        if (order.get("appointment_id") != null) db.update("update appointment set status='COMPLETED' where id=?", order.get("appointment_id"));
        recordEvent(id, "HANDOVER");
        return ApiResponse.ok(grooming(id));
    }

    @GetMapping("/grooming/{id}/events")
    public ApiResponse<?> events(@PathVariable long id) {
        var order = one("select * from grooming where id=?", id);
        if (role("OWNER")) require(((Number) order.get("customer_id")).longValue() == access.customerId());
        else if (role("GROOMER")) require(((Number) order.get("employee_id")).longValue() == access.employeeId());
        else require(role("MANAGER") || role("ADMIN") || role("RECEPTION"));
        return ApiResponse.ok(rows("select * from grooming_event where grooming_id=? order by id", id));
    }

    private void recordEvent(long id, String type) {
        db.update("insert into grooming_event(grooming_id,event_type,operator_name,created_at) values (?,?,?,?)", id, type, SecurityContextHolder.getContext().getAuthentication().getName(), java.time.LocalDateTime.now(clock));
    }

    @GetMapping("/grooming/{id}/settlement")
    public ApiResponse<?> settlement(@PathVariable long id) {
        var order = one("select * from grooming where id=?", id);
        if (role("OWNER")) require(((Number) order.get("customer_id")).longValue() == access.customerId());
        else require(role("MANAGER") || role("ADMIN") || role("RECEPTION"));
        var paid = rows("select * from service_settlement where grooming_id=?", id);
        return ApiResponse.ok(paid.isEmpty() ? settlementQuote(order) : paid.get(0));
    }

    private Map<String,Object> settlementQuote(Map<String,Object> order) {
        BigDecimal base;
        if (order.get("base_price") != null) base=(BigDecimal)order.get("base_price");
        else if (order.get("appointment_id") != null) base = (BigDecimal) one("select price from appointment where id=?", order.get("appointment_id")).get("price");
        else {
            var service=one("select * from service where id=?",order.get("service_id"));
            String size = text(one("select size_type from pet where id=?", order.get("pet_id")).get("size_type"));
            base=pricing.quote(service,size).price();
        }
        BigDecimal addons = db.queryForObject("select coalesce(sum(amount),0) from addon where grooming_id=? and status='APPROVED'", BigDecimal.class, order.get("id"));
        var result = new LinkedHashMap<String,Object>();
        result.put("grooming_id", order.get("id")); result.put("customer_id", order.get("customer_id"));
        result.put("base_amount", base); result.put("addon_amount", addons); result.put("amount", base.add(addons)); result.put("status", "UNPAID");
        return result;
    }

    @PostMapping("/grooming/{id}/settle")
    @Transactional
    public ApiResponse<?> settle(@PathVariable long id, @RequestBody Settlement form) {
        var order = one("select * from grooming where id=? for update", id);
        long customer = ((Number) order.get("customer_id")).longValue();
        String method = form.method();
        if (role("OWNER")) {
            require(customer == access.customerId());
            if (!"BALANCE".equals(method)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "宠主仅支持使用会员储值余额自助结算，次卡或现金请联系门店收银");
            }
        } else {
            require(role("MANAGER") || role("ADMIN") || role("RECEPTION"));
        }
        if (!"WAITING_PICKUP".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "完成护理后才能结算");
        var existingSettlement = rows("select grooming_id, status from service_settlement where grooming_id=?", id);
        if (!existingSettlement.isEmpty() && !"REFUNDED".equals(existingSettlement.get(0).get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该工单已经结算");
        }
        if (!rows("select id from addon where grooming_id=? and status='PENDING'", id).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先处理待确认的加项");
        if (method == null || !List.of("CASH", "BALANCE", "CARD", "ALIPAY").contains(method)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择现金、余额、次卡或支付宝");
        var quote = settlementQuote(order);
        BigDecimal amount = (BigDecimal) quote.get("amount"), addons = (BigDecimal) quote.get("addon_amount");
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        var now = java.time.LocalDateTime.now(clock);
        if ("CARD".equals(method)) {
            if (form.cardId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择适用次卡");
            String addonMethod = form.addonMethod() == null || form.addonMethod().isBlank() ? "CASH" : form.addonMethod();
            if (!List.of("CASH", "BALANCE").contains(addonMethod)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "加项部分请选择现金或余额结算");
            if (addons.signum() > 0 && "BALANCE".equals(addonMethod)) {
                var cardMatch = rows("select id from prepaid_card where id=? and customer_id=? and remaining>0 and id in (select card_id from prepaid_card_service where service_id=?)", form.cardId(), customer, order.get("service_id"));
                if (cardMatch.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "次卡不属于该宠主、次数不足或不适用于此服务");
                int balChanged = db.update("update customer set balance=balance-? where id=? and balance>=?", addons, customer, addons);
                if (balChanged != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "会员余额不足以支付加项费用");
                db.update("insert into balance_flow(customer_id,grooming_id,amount,operator_name,created_at) values (?,?,?,?,?)", customer, id, addons.negate(), operator, now);
            }
            int changed = db.update("update prepaid_card set remaining=remaining-1 where id=? and customer_id=? and remaining>0 and id in (select card_id from prepaid_card_service where service_id=?)", form.cardId(), customer, order.get("service_id"));
            if (changed != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "次卡不属于该宠主、次数不足或不适用于此服务");
            db.update("insert into prepaid_card_flow(card_id,grooming_id,change_times,operator_name,created_at) values (?,?,?,?,?)", form.cardId(), id, -1, operator, now);
        } else if ("BALANCE".equals(method)) {
            int changed = db.update("update customer set balance=balance-? where id=? and balance>=?", amount, customer, amount);
            if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "会员余额不足");
            db.update("insert into balance_flow(customer_id,grooming_id,amount,operator_name,created_at) values (?,?,?,?,?)", customer, id, amount.negate(), operator, now);
        }
        String tradeNo = form.tradeNo() != null ? form.tradeNo() : ("ALIPAY".equals(method) ? "POS_ALIPAY_" + System.currentTimeMillis() : null);
        if (existingSettlement.isEmpty()) {
            db.update("insert into service_settlement(grooming_id,customer_id,base_amount,addon_amount,amount,method,card_id,status,operator_name,paid_at,trade_no) values (?,?,?,?,?,?,?,?,?,?,?)",
                id, customer, quote.get("base_amount"), addons, amount, method, "CARD".equals(method) ? form.cardId() : null, "PAID", operator, now, tradeNo);
        } else {
            db.update("update service_settlement set base_amount=?, addon_amount=?, amount=?, method=?, card_id=?, status='PAID', operator_name=?, paid_at=?, refunded_at=NULL, refund_reason=NULL, trade_no=? where grooming_id=?",
                quote.get("base_amount"), addons, amount, method, "CARD".equals(method) ? form.cardId() : null, operator, now, tradeNo, id);
        }
        auditService.log("GROOMING_SETTLE", operator, "GROOMING", String.valueOf(id), "工单结算完成，支付方式: " + method + "，金额: " + amount + (tradeNo != null ? "，流水号: " + tradeNo : ""));
        notificationService.createNotification("OWNER", customer, "PAYMENT_SUCCESS", "工单结算成功", "您的洗护工单 #" + id + " 已完成结算，支付方式: " + method + "，金额: ￥" + amount, String.valueOf(id), false);
        return ApiResponse.ok(one("select * from service_settlement where grooming_id=?", id));
    }

    @PostMapping("/grooming/{id}/refund")
    @Transactional
    public ApiResponse<?> refund(@PathVariable long id, @RequestBody Refund form) {
        require(role("MANAGER") || role("ADMIN"));
        one("select id from grooming where id=? for update", id);
        var payment = one("select * from service_settlement where grooming_id=?", id);
        if (!"PAID".equals(payment.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "该结算已退款");
        if (form.reason() == null || form.reason().isBlank() || form.reason().length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入退款原因");
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        var now = java.time.LocalDateTime.now(clock);
        if ("CARD".equals(payment.get("method"))) {
            db.update("update prepaid_card set remaining=remaining+1 where id=?", payment.get("card_id"));
            db.update("insert into prepaid_card_flow(card_id,grooming_id,change_times,operator_name,created_at) values (?,?,?,?,?)", payment.get("card_id"), id, 1, operator, now);
            var balFlows = rows("select amount from balance_flow where grooming_id=? and amount<0 order by id desc", id);
            if (!balFlows.isEmpty()) {
                BigDecimal deducted = ((BigDecimal) balFlows.get(0).get("amount")).abs();
                db.update("update customer set balance=balance+? where id=?", deducted, payment.get("customer_id"));
                db.update("insert into balance_flow(customer_id,grooming_id,amount,operator_name,created_at) values (?,?,?,?,?)", payment.get("customer_id"), id, deducted, operator, now);
            }
        } else if ("BALANCE".equals(payment.get("method"))) {
            db.update("update customer set balance=balance+? where id=?", payment.get("amount"), payment.get("customer_id"));
            db.update("insert into balance_flow(customer_id,grooming_id,amount,operator_name,created_at) values (?,?,?,?,?)", payment.get("customer_id"), id, payment.get("amount"), operator, now);
        }
        db.update("update payment_order set status='REFUNDED' where grooming_id=? and status='SUCCESS'", id);
        db.update("update service_settlement set status='REFUNDED', refunded_at=?, refund_reason=? where grooming_id=?", now, form.reason().trim(), id);
        auditService.log("GROOMING_REFUND", operator, "GROOMING", String.valueOf(id), "退款原因: " + form.reason().trim());
        return ApiResponse.ok(one("select * from service_settlement where grooming_id=?", id));
    }

    @PostMapping("/pets")
    @Transactional
    public ApiResponse<?> createPet(@RequestBody PetForm form) {
        long customerId;
        if (role("OWNER")) {
            customerId = access.customerId();
            if (form.customerId() != null && form.customerId() != customerId) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能为自己的账户添加宠物档案");
            }
        } else {
            access.requireStaff();
            if (form.customerId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择所属宠主");
            one("select id from customer where id=?", form.customerId());
            customerId = form.customerId();
        }
        reject(CareTerms.petError(form.name(), form.species(), form.breed(), form.ageBand(), form.sizeType(), form.weight(), form.coat(), form.allergy(), form.skinHistory(), form.groomingHistory(), null));
        String note=CareTerms.summary(form.ageBand(),form.allergy(),form.skinHistory(),form.groomingHistory());
        var keys=new GeneratedKeyHolder();
        db.update(connection -> {
            var statement=connection.prepareStatement("insert into pet(customer_id,name,species,breed,size_type,weight,coat,health_note,age_band,allergy,skin_history,grooming_history) values (?,?,?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1,customerId); statement.setString(2,form.name().trim()); statement.setString(3,form.species().trim()); statement.setString(4,form.breed().trim());
            statement.setString(5,form.sizeType()); statement.setBigDecimal(6,form.weight()); statement.setString(7,form.coat().trim()); statement.setString(8,note);
            statement.setString(9,form.ageBand().trim()); statement.setString(10,form.allergy().trim()); statement.setString(11,form.skinHistory().trim()); statement.setString(12,form.groomingHistory().trim());
            return statement;
        },keys);
        long id=keys.getKey().longValue();
        return ApiResponse.ok(pet(id));
    }

    @PutMapping("/pets/{id}")
    @Transactional
    public ApiResponse<?> updatePet(@PathVariable long id, @RequestBody PetForm form) {
        var current = pet(id);
        long targetCustomerId = ((Number) current.get("customer_id")).longValue();
        if (role("OWNER")) {
            if (targetCustomerId != access.customerId()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能编辑自己的宠物档案");
            }
        } else if (role("GROOMER")) {
            access.requirePet(id);
        } else {
            access.requireStaff();
            if (form.customerId() != null) {
                one("select id from customer where id=?", form.customerId());
                targetCustomerId = form.customerId();
            }
        }
        BigDecimal currentWeight = new BigDecimal(current.get("weight").toString());
        reject(CareTerms.petError(form.name(), form.species(), form.breed(), form.ageBand(), form.sizeType(), form.weight(), form.coat(), form.allergy(), form.skinHistory(), form.groomingHistory(), currentWeight));
        String note = CareTerms.summary(form.ageBand(), form.allergy(), form.skinHistory(), form.groomingHistory());
        db.update("update pet set customer_id=?, name=?, species=?, breed=?, size_type=?, weight=?, coat=?, health_note=?, age_band=?, allergy=?, skin_history=?, grooming_history=? where id=?",
            targetCustomerId, form.name().trim(), form.species().trim(), form.breed().trim(), form.sizeType(), form.weight(), form.coat().trim(), note, form.ageBand().trim(), form.allergy().trim(), form.skinHistory().trim(), form.groomingHistory().trim(), id);
        return ApiResponse.ok(pet(id));
    }

    @PutMapping(value = "/pets/{id}/photo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> uploadPetPhoto(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        var current = pet(id);
        if (role("OWNER")) access.require(((Number) current.get("customer_id")).longValue() == access.customerId());
        else if (role("GROOMER")) access.requirePet(id);
        else access.requireStaff();
        photos.savePetImage(id, file);
        return ApiResponse.ok(pet(id));
    }

    @GetMapping("/pets/{id}/records")
    public ApiResponse<?> records(@PathVariable long id) {
        access.requirePet(id);
        pet(id);
        var list = rows("select * from health_record where pet_id=? order by recorded_on, id", id);
        list.forEach(this::presentRecord);
        return ApiResponse.ok(list);
    }

    @PostMapping("/pets/{id}/records")
    @Transactional
    public ApiResponse<?> addHealthRecord(@PathVariable long id, @RequestBody HealthEntryForm form) {
        access.requirePet(id);
        pet(id);
        if (form.weight() == null || form.weight().signum() <= 0 || form.weight().compareTo(new BigDecimal("999.99")) > 0 || form.weight().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的小数体重 (0.01 至 999.99)");
        }
        String skin = form.skinCode() != null && !form.skinCode().isBlank() ? form.skinCode() : "CLEAR";
        String coat = form.coatCode() != null && !form.coatCode().isBlank() ? form.coatCode() : "SMOOTH";
        String ear = form.earCode() != null && !form.earCode().isBlank() ? form.earCode() : "CLEAN";
        int score = form.score() != null ? form.score() : CareTerms.score(skin, coat, ear);
        String note = form.note() != null && !form.note().isBlank() ? form.note().trim() : "日常健康体检记录";
        if (note.length() > 300) note = note.substring(0, 300);

        LocalDate today = LocalDate.now(clock);
        db.update("insert into health_record(pet_id, grooming_id, employee_id, recorded_on, weight, skin_code, coat_code, ear_code, score, groomer_note) values (?,?,?,?,?,?,?,?,?,?)",
            id, null, null, today, form.weight(), skin, coat, ear, score, note);
        db.update("update pet set weight=?, health_note=? where id=?", form.weight(), note, id);
        var rec = one("select * from health_record where pet_id=? order by id desc limit 1", id);
        presentRecord(rec);
        return ApiResponse.ok(rec);
    }

    @PostMapping("/appointments/{id}/cancel") @Transactional
    public ApiResponse<?> cancel(@PathVariable long id) {
        var appointment = one("select * from appointment where id=? for update", id);
        long customerId = ((Number) appointment.get("customer_id")).longValue();
        if (role("OWNER")) {
            if (customerId != access.customerId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能取消自己的预约");
        } else {
            require(role("MANAGER") || role("ADMIN") || role("RECEPTION"));
        }
        if (!"CONFIRMED".equals(appointment.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "只有待到店的预约可以取消");
        db.update("update appointment set status='CANCELLED' where id=? and status='CONFIRMED'", id);
        return ApiResponse.ok(one("select * from appointment where id=?", id));
    }

    @PostMapping("/grooming")
    @Transactional
    public ApiResponse<?> walkIn(@RequestBody WalkIn form) {
        require(role("MANAGER") || role("ADMIN") || role("RECEPTION"));
        db.queryForObject("select id from booking_lock where id=1 for update",Integer.class);
        var pet = one("select * from pet where id=?", form.petId());
        var service = one("select * from service where id=?", form.serviceId());
        var employee = one("select * from employee where id=?", form.employeeId());
        if (!enabled(service)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该项目已停用");
        if (!enabled(employee)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该洗护师已停用");
        String species = pet.get("species").toString();
        if (!"ALL".equals(service.get("species")) && !species.equals(service.get("species"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目不适用于该宠物");
        }
        if (!species.equals(employee.get("species").toString()) || !employee.get("sizes").toString().contains(pet.get("size_type").toString())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这位洗护师不负责该类型宠物");
        }
        var quote = pricing.quote(service, pet.get("size_type").toString());
        BigDecimal basePrice = quote.price();
        int duration = quote.duration();
        LocalDate today = LocalDate.now(clock);
        java.time.LocalTime nowTime = java.time.LocalTime.now(clock).withSecond(0).withNano(0);
        java.time.LocalTime openTime = storeConfig.getOpenTime();
        java.time.LocalTime closeTime = storeConfig.getCloseTime();
        int buffer = storeConfig.getBufferMinutes();
        int nowMin = nowTime.getHour() * 60 + nowTime.getMinute();
        int openMin = openTime.getHour() * 60 + openTime.getMinute();
        int closeMin = closeTime.getHour() * 60 + closeTime.getMinute();
        int earliestMin = Math.max(openMin, nowMin);
        int latestMin = closeMin - duration - buffer;
        if (earliestMin > latestMin) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前营业时间不足以完成该服务");
        }

        var shifts = rows("select * from employee_schedule where schedule_date=? and employee_id=?", today, employee.get("id"));
        java.time.LocalTime shiftStart = openTime;
        java.time.LocalTime shiftEnd = closeTime;
        if (!shifts.isEmpty()) {
            var configured = shifts.get(0);
            if ("OFF".equals(configured.get("status"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该洗护师今日休假，无法接单");
            }
            shiftStart = ((java.sql.Time) configured.get("start_time")).toLocalTime();
            shiftEnd = ((java.sql.Time) configured.get("end_time")).toLocalTime();
        }

        var resources = form.resourceId() != null
            ? rows("select * from resource where id=? and species=? and enabled<>0", form.resourceId(), species)
            : rows("select * from resource where species=? and enabled<>0 order by id", species);
        if (resources.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有适用于该宠物的洗护工位");
        var active = rows("select * from appointment where appointment_date=? and status not in ('CANCELLED','NO_SHOW')", today);
        long empId = ((Number) employee.get("id")).longValue();
        long petId = ((Number) pet.get("id")).longValue();

        java.time.LocalTime startTime = null;
        java.time.LocalTime endTime = null;
        Long chosenResourceId = null;

        for (int candidateMin = earliestMin; candidateMin <= latestMin; candidateMin += 15) {
            java.time.LocalTime candStart = java.time.LocalTime.of(candidateMin / 60, candidateMin % 60);
            java.time.LocalTime candEnd = candStart.plusMinutes(duration);
            if (candStart.isBefore(shiftStart) || candEnd.plusMinutes(15).isAfter(shiftEnd)) {
                continue;
            }
            boolean empOrPetBusy = false;
            for (var a : active) {
                java.time.LocalTime exStart = ((java.sql.Time) a.get("start_time")).toLocalTime();
                java.time.LocalTime exEnd = ((java.sql.Time) a.get("end_time")).toLocalTime();
                if (((Number) a.get("pet_id")).longValue() == petId && SchedulingRules.conflicts(candStart, candEnd, exStart, exEnd, 0)) {
                    empOrPetBusy = true; break;
                }
                if (((Number) a.get("employee_id")).longValue() == empId && SchedulingRules.conflicts(candStart, candEnd, exStart, exEnd, 15)) {
                    empOrPetBusy = true; break;
                }
            }
            if (empOrPetBusy) continue;
            for (var r : resources) {
                long rId = ((Number) r.get("id")).longValue();
                boolean resConflict = false;
                for (var a : active) {
                    if (a.get("resource_id") != null && ((Number) a.get("resource_id")).longValue() == rId) {
                        java.time.LocalTime exStart = ((java.sql.Time) a.get("start_time")).toLocalTime();
                        java.time.LocalTime exEnd = ((java.sql.Time) a.get("end_time")).toLocalTime();
                        if (SchedulingRules.conflicts(candStart, candEnd, exStart, exEnd, 15)) { resConflict = true; break; }
                    }
                }
                if (!resConflict) {
                    startTime = candStart;
                    endTime = candEnd;
                    chosenResourceId = rId;
                    break;
                }
            }
            if (chosenResourceId != null) break;
        }
        if (chosenResourceId == null || startTime == null || endTime == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前洗护师或工位今日时段已满，请选择其他洗护师");
        }
        final long finalResourceId = chosenResourceId;
        final java.time.LocalTime finalStart = startTime;
        final java.time.LocalTime finalEnd = endTime;

        var apptKeys = new GeneratedKeyHolder();
        db.update(connection -> {
            var st = connection.prepareStatement("insert into appointment(customer_id,pet_id,service_id,employee_id,resource_id,appointment_date,start_time,end_time,price,status) values (?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            st.setObject(1, pet.get("customer_id")); st.setObject(2, pet.get("id"));
            st.setObject(3, service.get("id")); st.setObject(4, employee.get("id"));
            st.setLong(5, finalResourceId); st.setObject(6, today);
            st.setObject(7, finalStart); st.setObject(8, finalEnd);
            st.setBigDecimal(9, basePrice); st.setString(10, "IN_SERVICE");
            return st;
        }, apptKeys);
        long appointmentId = apptKeys.getKey().longValue();

        String code = pickupCode();
        var keys = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement("insert into grooming(pet_id,customer_id,employee_id,service_id,stage,status,pickup_code,appointment_id,resource_id,base_price) values (?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, pet.get("id")); statement.setObject(2, pet.get("customer_id"));
            statement.setObject(3, employee.get("id")); statement.setObject(4, service.get("id"));
            statement.setString(5, "CHECK_IN"); statement.setString(6, "GROOMING"); statement.setString(7, code);
            statement.setLong(8, appointmentId); statement.setLong(9, finalResourceId);
            statement.setBigDecimal(10, basePrice);
            return statement;
        }, keys);
        long id = keys.getKey().longValue();
        recordEvent(id, "CHECK_IN");
        return ApiResponse.ok(grooming(id));
    }

    @PostMapping("/grooming/{id}/assessment")
    @Transactional
    public ApiResponse<?> assess(@PathVariable long id, @RequestBody Assessment form) {
        require(role("GROOMER") || role("MANAGER") || role("ADMIN"));
        var order = one("select * from grooming where id=? for update", id);
        if (role("GROOMER") && ((Number) order.get("employee_id")).longValue() != access.employeeId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能记录分配给自己的工单");
        }
        if (!"GROOMING".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "只有护理中的工单可以记录健康评分");
        if (!"TRIMMING".equals(order.get("stage"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "完成前面的护理阶段后才能记录评分");
        if (!rows("select id from health_record where grooming_id=?", id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这次洗护已经记录过健康评分");
        }
        if (form.weight() == null || form.weight().scale() > 2 || form.weight().compareTo(BigDecimal.ZERO) <= 0 || form.weight().compareTo(new BigDecimal("999.99")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入大于 0 且不超过 999.99 千克的实测体重，最多两位小数");
        String groomerNote = form.groomerNote() != null ? form.groomerNote().trim() : null;
        if (groomerNote != null && groomerNote.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "护理寄语不超过 300 字");
        }
        if (groomerNote != null && groomerNote.isEmpty()) groomerNote = null;
        int score;
        try { score = CareTerms.score(form.skin(), form.coat(), form.ear()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
        inventory.consumeCare(((Number) order.get("service_id")).longValue(), id);
        long recordedEmployeeId = ((Number) order.get("employee_id")).longValue();
        db.update("insert into health_record(pet_id,grooming_id,employee_id,recorded_on,weight,skin_code,coat_code,ear_code,score,groomer_note) values (?,?,?,?,?,?,?,?,?,?)",
            order.get("pet_id"), id, recordedEmployeeId, LocalDate.now(clock), form.weight(), form.skin(), form.coat(), form.ear(), score, groomerNote);
        db.update("update pet set weight=? where id=?", form.weight(), order.get("pet_id"));
        db.update("update grooming set stage='DONE', status='WAITING_PICKUP' where id=?", id);
        recordEvent(id, "DONE");
        var petInfo = one("select name from pet where id=?", order.get("pet_id"));
        notificationService.createNotification("OWNER", ((Number) order.get("customer_id")).longValue(), "PICKUP_READY", "护理完成待接宠", "您的爱宠 " + petInfo.get("name") + " 护理已完成，请凭取宠码 " + order.get("pickup_code") + " 到店提宠", String.valueOf(id), false);
        var saved = one("select * from health_record where grooming_id=?", id);
        presentRecord(saved);
        saved.put("status", "WAITING_PICKUP");
        return ApiResponse.ok(saved);
    }

    @PostMapping("/services")
    public ApiResponse<?> createService(@RequestBody ServiceForm form) {
        require(role("MANAGER") || role("ADMIN"));
        reject(CareTerms.serviceError(form.name(), form.category(), form.price(), form.duration(), form.species()));
        var keys=new GeneratedKeyHolder();
        db.update(connection -> {
            var statement=connection.prepareStatement("insert into service(name,category,price,duration,species,enabled) values (?,?,?,?,?,1)",Statement.RETURN_GENERATED_KEYS);
            statement.setString(1,form.name().trim()); statement.setString(2,form.category()); statement.setBigDecimal(3,form.price().setScale(2,RoundingMode.HALF_UP));
            statement.setInt(4,form.duration()); statement.setString(5,form.species()); return statement;
        },keys);
        long id=keys.getKey().longValue();
        return ApiResponse.ok(service(id));
    }

    @PutMapping("/services/{id}")
    public ApiResponse<?> updateService(@PathVariable long id, @RequestBody ServiceUpdate form) {
        require(role("MANAGER") || role("ADMIN"));
        var existing = service(id);
        if (form.price() == null || form.enabled() == null || form.price().scale() > 2 || form.price().compareTo(BigDecimal.ONE) < 0 || form.price().compareTo(new BigDecimal("2000")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写 1 到 2000 元之间的价格，并选择启用或停用");
        }
        String name = form.name() != null ? form.name() : existing.get("name").toString();
        String category = form.category() != null ? form.category() : existing.get("category").toString();
        Integer duration = form.duration() != null ? form.duration() : ((Number) existing.get("duration")).intValue();
        String species = form.species() != null ? form.species() : existing.get("species").toString();
        reject(CareTerms.serviceError(name, category, form.price(), duration, species));
        db.update("update service set name=?, category=?, duration=?, species=?, price=?, enabled=? where id=?",
            name.trim(), category, duration, species, form.price().setScale(2, RoundingMode.HALF_UP), form.enabled() ? 1 : 0, id);
        return ApiResponse.ok(service(id));
    }

    @GetMapping("/orders")
    public ApiResponse<?> orders() {
        if (role("GROOMER")) return ApiResponse.ok(List.of());
        if (!role("OWNER")) access.requireStaff();
        String sql = "select o.*, c.name as customer_name, c.phone as phone from retail_order o left join customer c on o.customer_id=c.id";
        var list = role("OWNER")
            ? rows(sql + " where o.customer_id=? order by o.id desc", access.customerId())
            : rows(sql + " order by o.id desc");
        list.forEach(this::presentOrder);
        return ApiResponse.ok(list);
    }

    @PostMapping("/orders")
    @Transactional
    public ApiResponse<?> order(@RequestBody OrderForm form) {
        long customer;
        if (role("OWNER")) {
            customer = access.customerId();
            if (form.customerId() != null && form.customerId() != customer) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能为自己的账户下单");
        } else {
            access.requireStaff();
            if (form.customerId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择所属宠主");
            one("select id from customer where id=?", form.customerId());
            customer = form.customerId();
        }
        if (form.quantity() < 1 || form.quantity() > 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 1 到 3 件");
        String request;
        try { request=java.util.UUID.fromString(form.requestId()).toString(); if(!request.equalsIgnoreCase(form.requestId())) throw new IllegalArgumentException(); }
        catch(Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请求编号无效"); }
        db.queryForObject("select id from sale_lock where id=1 for update",Integer.class);
        var previous=rows("select * from retail_order where request_id=?",request);
        if(!previous.isEmpty()) {
            var existing=previous.get(0);
            if(((Number)existing.get("customer_id")).longValue()!=customer || ((Number)existing.get("product_id")).longValue()!=form.productId() || ((Number)existing.get("quantity")).intValue()!=form.quantity()) throw new ResponseStatusException(HttpStatus.CONFLICT,"请求编号已用于其他订单");
            presentOrder(existing); return ApiResponse.ok(existing);
        }
        var product = one("select * from product where id=? for update", form.productId());
        int stockUnits = Math.multiplyExact(form.quantity(), ((Number) product.get("sale_units")).intValue());
        int stock = ((Number) product.get("stock")).intValue();
        if (stock < stockUnits) throw new ResponseStatusException(HttpStatus.CONFLICT, "库存不足");
        BigDecimal price = new BigDecimal(product.get("price").toString());
        if (price.compareTo(BigDecimal.ZERO) <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该商品暂未开售");
        BigDecimal amount = price.multiply(BigDecimal.valueOf(form.quantity())).setScale(2, RoundingMode.HALF_UP);
        int changed = db.update("update product set stock=stock-? where id=? and stock>=?", stockUnits, product.get("id"), stockUnits);
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "库存不足，请重新选择");
        var keys = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement("insert into retail_order(customer_id,product_id,product_name,quantity,unit_price,amount,status,created_at,stock_units,request_id) values (?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, customer); statement.setObject(2, product.get("id")); statement.setString(3, product.get("name").toString());
            statement.setInt(4, form.quantity()); statement.setBigDecimal(5, price); statement.setBigDecimal(6, amount);
            statement.setString(7, "SUBMITTED"); statement.setObject(8, java.time.LocalDateTime.now(clock));
            statement.setInt(9,stockUnits); statement.setString(10,request);
            return statement;
        }, keys);
        long id = keys.getKey().longValue();
        inventory.recordSale(((Number) product.get("id")).longValue(), stockUnits, id);
        retailEvent(id,"SUBMITTED",amount,"商品订单已提交");
        var saved = one("select * from retail_order where id=?", id);
        presentOrder(saved);
        return ApiResponse.ok(saved);
    }

    @PostMapping("/orders/{id}/settle") @Transactional public ApiResponse<?> settleRetail(@PathVariable long id, @RequestBody(required = false) RetailSettle form) {
        var order=one("select * from retail_order where id=? for update",id);
        String method = form != null && form.method() != null && !form.method().isBlank() ? form.method() : "CASH";
        if (role("OWNER")) {
            require(((Number) order.get("customer_id")).longValue() == access.customerId());
            if (!"BALANCE".equals(method)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "宠主仅支持使用会员储值余额自助支付");
        } else {
            access.requireStaff();
        }
        if(!"SUBMITTED".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"订单不在待收款状态");
        if (!List.of("CASH", "BALANCE").contains(method)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择现金或会员余额收款");
        if ("BALANCE".equals(method)) {
            long customerId = ((Number) order.get("customer_id")).longValue();
            BigDecimal amount = (BigDecimal) order.get("amount");
            int changed = db.update("update customer set balance=balance-? where id=? and balance>=?", amount, customerId, amount);
            if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "会员余额不足");
            db.update("insert into balance_flow(customer_id,grooming_id,amount,operator_name,created_at) values (?,NULL,?,?,?)",
                customerId, amount.negate(), SecurityContextHolder.getContext().getAuthentication().getName(), java.time.LocalDateTime.now(clock));
        }
        db.update("update retail_order set status='PAID', pay_method=? where id=?", method, id);
        retailEvent(id,"PAID",order.get("amount"),"BALANCE".equals(method) ? "已扣减会员余额" : "已确认收取现金");
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.log("ORDER_SETTLE", operator, "ORDER", String.valueOf(id), "商品订单结算完成，方式: " + method + "，金额: " + order.get("amount"));
        var result=one("select * from retail_order where id=?",id);presentOrder(result);return ApiResponse.ok(result);
    }
    @PostMapping("/orders/{id}/cancel") @Transactional public ApiResponse<?> cancelRetail(@PathVariable long id) {
        var order=one("select * from retail_order where id=? for update",id);
        if(role("OWNER")) require(((Number)order.get("customer_id")).longValue()==access.customerId()); else access.requireStaff();
        if(!"SUBMITTED".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"只有待收款的订单可以取消");
        inventory.restoreSale(((Number)order.get("product_id")).longValue(),((Number)order.get("stock_units")).intValue(),id,"CANCEL");
        db.update("update retail_order set status='CANCELLED' where id=?",id); retailEvent(id,"CANCELLED",order.get("amount"),"取消未支付商品订单");
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.log("ORDER_CANCEL", operator, "ORDER", String.valueOf(id), "商品订单已取消，金额: " + order.get("amount"));
        var result=one("select * from retail_order where id=?",id);presentOrder(result);return ApiResponse.ok(result);
    }
    @PostMapping("/orders/{id}/refund") @Transactional public ApiResponse<?> refundRetail(@PathVariable long id,@RequestBody Refund form) {
        require(role("MANAGER") || role("ADMIN"));
        var order=one("select * from retail_order where id=? for update",id);
        if(!"PAID".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已收款订单可以退款");
        if(form.reason()==null || form.reason().isBlank() || form.reason().length()>255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入退款原因");
        String payMethod = order.get("pay_method") != null ? order.get("pay_method").toString() : null;
        boolean isBalance = "BALANCE".equals(payMethod);
        if (payMethod == null) {
            var paidEvents = rows("select reason from retail_order_event where order_id=? and action='PAID' order by id desc", id);
            isBalance = !paidEvents.isEmpty() && text(paidEvents.get(0).get("reason")).contains("会员余额");
        }
        if (isBalance) {
            long customerId = ((Number) order.get("customer_id")).longValue();
            BigDecimal amount = (BigDecimal) order.get("amount");
            db.update("update customer set balance=balance+? where id=?", amount, customerId);
            db.update("insert into balance_flow(customer_id,grooming_id,amount,operator_name,created_at) values (?,NULL,?,?,?)",
                customerId, amount, SecurityContextHolder.getContext().getAuthentication().getName(), java.time.LocalDateTime.now(clock));
        }
        inventory.restoreSale(((Number)order.get("product_id")).longValue(),((Number)order.get("stock_units")).intValue(),id,"REFUND");
        db.update("update retail_order set status='REFUNDED' where id=?",id); retailEvent(id,"REFUNDED",order.get("amount"),form.reason().trim());
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.log("ORDER_REFUND", operator, "ORDER", String.valueOf(id), "商品退款原因: " + form.reason().trim());
        var result=one("select * from retail_order where id=?",id);presentOrder(result);return ApiResponse.ok(result);
    }
    private void retailEvent(long id,String action,Object amount,String reason) {
        db.update("insert into retail_order_event(order_id,action,amount,reason,operator_name,created_at) values (?,?,?,?,?,?)",id,action,amount,reason,SecurityContextHolder.getContext().getAuthentication().getName(),java.time.LocalDateTime.now(clock));
    }

    private Map<String, Object> pet(long id) {
        var found = one("select * from pet where id=?", id);
        CareTerms.decorate(found);
        attachPhoto(found);
        return found;
    }
    private void attachPhoto(Map<String, Object> pet) {
        long id = ((Number) pet.get("id")).longValue();
        if (photos.hasPetImage(id)) pet.put("photo_url", photos.petUrl(id));
    }

    private Map<String, Object> service(long id) {
        var found = one("select * from service where id=?", id);
        found.put("enabled", enabled(found));
        return found;
    }

    private Map<String, Object> grooming(long id) {
        return one("select g.*, p.name pet_name, e.name employee_name, s.name service_name from grooming g join pet p on p.id=g.pet_id join employee e on e.id=g.employee_id join service s on s.id=g.service_id where g.id=?", id);
    }

    private void presentRecord(Map<String, Object> row) {
        row.put("recorded_on", dateText(row.get("recorded_on")));
        row.put("skin_label", CareTerms.SKIN_LABEL.getOrDefault(text(row.get("skin_code")), ""));
        row.put("coat_label", CareTerms.COAT_LABEL.getOrDefault(text(row.get("coat_code")), ""));
        row.put("ear_label", CareTerms.EAR_LABEL.getOrDefault(text(row.get("ear_code")), ""));
    }

    private void presentOrder(Map<String, Object> row) {
        Object created = row.get("created_at");
        if (created instanceof java.sql.Timestamp timestamp) row.put("created_at", timestamp.toLocalDateTime().format(STAMP));
        else if (created instanceof java.time.LocalDateTime time) row.put("created_at", time.format(STAMP));
    }

    private String dateText(Object value) {
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof java.time.LocalDate date) return date.toString();
        return text(value);
    }

    private boolean enabled(Map<String, Object> service) {
        Object value = service.get("enabled");
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.intValue() != 0;
        return value == null || !"0".equals(value.toString());
    }
    private void presentService(Map<String, Object> service) {
        service.put("enabled", enabled(service));
        service.put("size_rules", pricing.rules(((Number) service.get("id")).longValue()));
    }

    private String pickupCode() {
        for (int i = 0; i < 6; i++) {
            String code = String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
            if (rows("select id from grooming where pickup_code=?", code).isEmpty()) return code;
        }
        return String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    private boolean role(String name) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(item -> ("ROLE_" + name).equals(item.getAuthority()));
    }

    private void require(boolean allowed) {
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有操作权限");
    }

    private void reject(String message) {
        if (message != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private List<Map<String, Object>> rows(String sql, Object... args) { return db.queryForList(sql, args); }

    private Map<String, Object> one(String sql, Object... args) {
        var found = rows(sql, args);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return new LinkedHashMap<>(found.get(0));
    }

    private String text(Object value) { return value == null ? "" : value.toString(); }
}
