package com.petcare.grooming.booking;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.catalog.ServicePricing;
import com.petcare.grooming.store.StoreConfigService;
import com.petcare.grooming.store.NotificationService;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AppointmentController {
    private final JdbcTemplate db;
    private final Clock clock;
    private final AccountAccess access;
    private final ServicePricing pricing;
    private final StoreConfigService storeConfig;
    private final NotificationService notificationService;

    AppointmentController(JdbcTemplate db, Clock clock, AccountAccess access, ServicePricing pricing, StoreConfigService storeConfig, NotificationService notificationService) {
        this.db = db;
        this.clock = clock;
        this.access = access;
        this.pricing = pricing;
        this.storeConfig = storeConfig;
        this.notificationService = notificationService;
    }

    public record Booking(long customerId, long petId, long serviceId, String date, String startTime, Long preferredEmployeeId) {}
    public record Reschedule(String date, String startTime) {}
    private record Allocation(LocalDate date, LocalTime start, LocalTime end, Map<String, Object> employee, Map<String, Object> resource, BigDecimal price) {}

    @GetMapping("/appointments")
    public ApiResponse<?> appointments() {
        String sql = "select a.*, p.name pet_name, s.name service_name, e.name employee_name, r.name resource_name from appointment a join pet p on p.id=a.pet_id join service s on s.id=a.service_id join employee e on e.id=a.employee_id join resource r on r.id=a.resource_id";
        if (access.role("OWNER")) return ApiResponse.ok(rows(sql + " where a.customer_id=? order by appointment_date, start_time", access.customerId()));
        if (access.role("GROOMER")) return ApiResponse.ok(rows(sql + " where a.employee_id=? order by appointment_date, start_time", access.employeeId()));
        access.requireStaff();
        return ApiResponse.ok(rows(sql + " order by appointment_date, start_time"));
    }

    @PostMapping("/appointments")
    @Transactional
    public synchronized ApiResponse<?> book(@RequestBody Booking booking) {
        access.require(access.role("OWNER") || access.role("MANAGER") || access.role("ADMIN") || access.role("RECEPTION"));
        if (access.role("OWNER")) access.require(booking.customerId() == access.customerId());
        db.queryForObject("select id from booking_lock where id=1 for update", Integer.class);
        var pet = one("select * from pet where id=? and customer_id=?", booking.petId(), booking.customerId());
        var service = one("select * from service where id=?", booking.serviceId());
        if (!enabled(service)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该项目已停用");
        var slot = allocate(pet, service, booking.date(), booking.startTime(), null, null, null, booking.preferredEmployeeId());
        db.update("insert into appointment(customer_id,pet_id,service_id,employee_id,resource_id,appointment_date,start_time,end_time,price,status) values (?,?,?,?,?,?,?,?,?,?)",
            booking.customerId(), booking.petId(), booking.serviceId(), slot.employee().get("id"), slot.resource().get("id"), slot.date(), slot.start(), slot.end(), slot.price(), "CONFIRMED");
        notificationService.createNotification("OWNER", booking.customerId(), "APPOINTMENT", "预约已成功提交", "您的爱宠 " + pet.get("name") + " 已预约 " + booking.date() + " " + booking.startTime() + " 的洗护服务", String.valueOf(booking.petId()), false);
        notificationService.createNotification("STORE", null, "APPOINTMENT", "新到预约待接待", "客户预约 " + pet.get("name") + " 于 " + booking.date() + " " + booking.startTime() + " 到店", String.valueOf(booking.petId()), false);
        return ApiResponse.ok(Map.of("employee", slot.employee().get("name"), "resource", slot.resource().get("name"), "endTime", slot.end().toString(), "price", slot.price()));
    }

    @PostMapping("/appointments/{id}/reschedule")
    @Transactional
    public ApiResponse<?> reschedule(@PathVariable long id, @RequestBody Reschedule form) {
        access.require(access.role("OWNER") || access.role("MANAGER") || access.role("ADMIN") || access.role("RECEPTION"));
        db.queryForObject("select id from booking_lock where id=1 for update", Integer.class);
        var appointment = one("select * from appointment where id=? for update", id);
        if (access.role("OWNER")) access.require(((Number) appointment.get("customer_id")).longValue() == access.customerId());
        if (!"CONFIRMED".equals(appointment.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "只有待到店的预约可以改期");
        var pet = one("select * from pet where id=?", appointment.get("pet_id"));
        var service = one("select * from service where id=?", appointment.get("service_id"));
        int duration = (int) java.time.Duration.between(((java.sql.Time) appointment.get("start_time")).toLocalTime(), ((java.sql.Time) appointment.get("end_time")).toLocalTime()).toMinutes();
        var slot = allocate(pet, service, form.date(), form.startTime(), id, duration, (BigDecimal) appointment.get("price"), null);
        db.update("update appointment set appointment_date=?,start_time=?,end_time=?,employee_id=?,resource_id=? where id=?", slot.date(), slot.start(), slot.end(), slot.employee().get("id"), slot.resource().get("id"), id);
        return ApiResponse.ok(one("select * from appointment where id=?", id));
    }

    private Allocation allocate(Map<String, Object> pet, Map<String, Object> service, String requestedDate, String requestedStart, Long excludedId, Integer durationOverride, BigDecimal priceOverride, Long preferredEmployeeId) {
        String species = pet.get("species").toString();
        if (!"ALL".equals(service.get("species")) && !species.equals(service.get("species"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目不适用于该宠物");
        LocalDate date;
        LocalTime start;
        try { date = LocalDate.parse(requestedDate); start = LocalTime.parse(requestedStart); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期或时间格式无效"); }
        var quote = pricing.quote(service, pet.get("size_type").toString());
        int minutes = durationOverride == null ? quote.duration() : durationOverride;
        int startMinute = start.getHour() * 60 + start.getMinute(), endMinute = startMinute + minutes;
        LocalTime openTime = storeConfig.getOpenTime();
        LocalTime closeTime = storeConfig.getCloseTime();
        int buffer = storeConfig.getBufferMinutes();
        int openMinute = openTime.getHour() * 60 + openTime.getMinute();
        int closeMinute = closeTime.getHour() * 60 + closeTime.getMinute();
        if (!LocalDateTime.of(date, start).isAfter(LocalDateTime.now(clock)) || startMinute < openMinute || endMinute + buffer > closeMinute) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择营业时间内的未来时段");
        LocalTime end = start.plusMinutes(minutes);
        var active = new ArrayList<>(rows("select * from appointment where appointment_date=? and status not in ('CANCELLED','NO_SHOW')", date));
        if (excludedId != null) active.removeIf(row -> ((Number) row.get("id")).longValue() == excludedId);
        for (var row : active) {
            if (((Number) row.get("pet_id")).longValue() != ((Number) pet.get("id")).longValue()) continue;
            LocalTime existingStart = ((java.sql.Time) row.get("start_time")).toLocalTime(), existingEnd = ((java.sql.Time) row.get("end_time")).toLocalTime();
            if (SchedulingRules.conflicts(start, end, existingStart, existingEnd, 0)) throw new ResponseStatusException(HttpStatus.CONFLICT, "这只宠物在该时段已有预约");
        }
        boolean knownSpecies = "DOG".equals(species) || "CAT".equals(species);
        var employees = preferredEmployeeId != null
            ? rows("select * from employee where id=? and enabled<>0 and (species=? or species='ALL') and sizes like ?", preferredEmployeeId, species, "%" + pet.get("size_type") + "%")
            : rows("select * from employee where enabled<>0 and (species=? or species='ALL') and sizes like ? order by id", species, "%" + pet.get("size_type") + "%");
        if (!knownSpecies && employees.isEmpty()) {
            employees = preferredEmployeeId != null
                ? rows("select * from employee where id=? and enabled<>0 and sizes like ?", preferredEmployeeId, "%" + pet.get("size_type") + "%")
                : rows("select * from employee where enabled<>0 and sizes like ? order by id", "%" + pet.get("size_type") + "%");
        }
        if (preferredEmployeeId != null && employees.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选洗护师不适用于该宠物或已停用");
        var resources = rows("select * from resource where enabled<>0 and (species=? or species='ALL') order by id", species);
        if (!knownSpecies && resources.isEmpty()) resources = rows("select * from resource where enabled<>0 order by id");
        var shifts = rows("select * from employee_schedule where schedule_date=?", date);
        for (var employee : employees) for (var resource : resources) {
            boolean conflict = false;
            var shift = shifts.stream().filter(item -> ((Number) item.get("employee_id")).longValue() == ((Number) employee.get("id")).longValue()).findFirst();
            if (shift.isPresent()) {
                var configured = shift.get();
                if ("OFF".equals(configured.get("status"))) continue;
                LocalTime shiftStart = ((java.sql.Time) configured.get("start_time")).toLocalTime(), shiftEnd = ((java.sql.Time) configured.get("end_time")).toLocalTime();
                if (start.isBefore(shiftStart) || end.plusMinutes(15).isAfter(shiftEnd)) continue;
            }
            for (var row : active) {
                if (((Number) row.get("employee_id")).longValue() != ((Number) employee.get("id")).longValue() && ((Number) row.get("resource_id")).longValue() != ((Number) resource.get("id")).longValue()) continue;
                LocalTime existingStart = ((java.sql.Time) row.get("start_time")).toLocalTime(), existingEnd = ((java.sql.Time) row.get("end_time")).toLocalTime();
                if (SchedulingRules.conflicts(start, end, existingStart, existingEnd, 15)) { conflict = true; break; }
            }
            if (!conflict) {
                var price = priceOverride == null ? quote.price() : priceOverride;
                return new Allocation(date, start, end, employee, resource, price);
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "该时段没有可用洗护师或工位，请选择其他时间");
    }

    private List<Map<String, Object>> rows(String sql, Object... args) { return db.queryForList(sql, args); }
    private Map<String, Object> one(String sql, Object... args) {
        var found = rows(sql, args);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return found.get(0);
    }
    private static boolean enabled(Map<String, Object> row) {
        Object value = row.get("enabled");
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.intValue() != 0;
        return value == null || !"0".equals(value.toString());
    }
}
