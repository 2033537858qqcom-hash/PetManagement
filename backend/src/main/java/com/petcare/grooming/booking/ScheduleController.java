package com.petcare.grooming.booking;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.store.StoreConfigService;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;

@RestController @RequestMapping("/api/schedules")
public class ScheduleController {
    public record Shift(String status,String startTime,String endTime) {}
    private final JdbcTemplate db; private final AccountAccess access; private final Clock clock; private final StoreConfigService storeConfig;
    ScheduleController(JdbcTemplate db,AccountAccess access,Clock clock,StoreConfigService storeConfig){this.db=db;this.access=access;this.clock=clock;this.storeConfig=storeConfig;}
    private LocalDate date(String value) { try{return LocalDate.parse(value);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"排班日期无效");} }
    @GetMapping public ApiResponse<?> list(@RequestParam String date) {
        LocalDate day=date(date);
        if(access.role("GROOMER")) return ApiResponse.ok(db.queryForList("select * from employee_schedule where schedule_date=? and employee_id=?",day,access.employeeId()));
        access.requireStaff();return ApiResponse.ok(db.queryForList("select * from employee_schedule where schedule_date=? order by employee_id",day));
    }
    @PutMapping("/{employeeId}/{date}") @Transactional public ApiResponse<?> set(@PathVariable long employeeId,@PathVariable String date,@RequestBody Shift shift) {
        access.require(access.role("MANAGER") || access.role("ADMIN"));
        LocalDate day=date(date);
        if(day.isBefore(LocalDate.now(clock))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不能修改过去的排班");
        if(shift.status()==null || !List.of("WORK","OFF").contains(shift.status())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择上班或休假");
        LocalTime start=null,end=null;
        if("WORK".equals(shift.status())) {
            try {start=LocalTime.parse(shift.startTime());end=LocalTime.parse(shift.endTime());} catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入有效的上下班时间");}
            LocalTime openTime = storeConfig.getOpenTime();
            LocalTime closeTime = storeConfig.getCloseTime();
            if(start.isBefore(openTime) || end.isAfter(closeTime) || !end.isAfter(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"排班需位于 09:00–21:00 且结束晚于开始");
        }
        db.queryForObject("select id from booking_lock where id=1 for update",Integer.class);
        if(db.queryForList("select id from employee where id=?",employeeId).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"洗护师不存在");
        var appointments=db.queryForList("select start_time,end_time from appointment where employee_id=? and appointment_date=? and status not in ('CANCELLED','NO_SHOW')",employeeId,day);
        for(var appointment:appointments) {
            LocalTime bookedStart=((java.sql.Time)appointment.get("start_time")).toLocalTime(),bookedEnd=((java.sql.Time)appointment.get("end_time")).toLocalTime();
            if("OFF".equals(shift.status()) || bookedStart.isBefore(start) || bookedEnd.plusMinutes(storeConfig.getBufferMinutes()).isAfter(end)) throw new ResponseStatusException(HttpStatus.CONFLICT,"已有预约与调整后的排班冲突，请先处理预约");
        }
        boolean exists=!db.queryForList("select employee_id from employee_schedule where employee_id=? and schedule_date=?",employeeId,day).isEmpty();
        if(exists) db.update("update employee_schedule set status=?,start_time=?,end_time=? where employee_id=? and schedule_date=?",shift.status(),start,end,employeeId,day);
        else db.update("insert into employee_schedule(status,start_time,end_time,employee_id,schedule_date) values (?,?,?,?,?)",shift.status(),start,end,employeeId,day);
        return ApiResponse.ok(db.queryForMap("select * from employee_schedule where employee_id=? and schedule_date=?",employeeId,day));
    }
}
