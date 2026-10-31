package com.petcare.grooming.store;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final JdbcTemplate db;
    private final Clock clock;
    private final AccountAccess access;

    AnalyticsController(JdbcTemplate db, Clock clock, AccountAccess access) {
        this.db = db;
        this.clock = clock;
        this.access = access;
    }

    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard() {
        access.requireStaff();
        var result = new LinkedHashMap<String, Object>();
        LocalDate today = LocalDate.now(clock);
        result.put("todayAppointments", db.queryForObject("select count(*) from appointment where appointment_date = ?", Integer.class, today));
        result.put("inService", db.queryForObject("select count(*) from grooming where status = 'GROOMING'", Integer.class));
        result.put("pendingAddons", db.queryForObject("select count(*) from addon where status = 'PENDING'", Integer.class));
        result.put("lowStock", db.queryForObject("select count(*) from product where stock <= threshold", Integer.class));
        var todayService = nz(db.queryForObject("select coalesce(sum(amount),0) from service_settlement where status='PAID' and cast(paid_at as date) = ?", BigDecimal.class, today));
        var todayRetail = nz(db.queryForObject("select coalesce(sum(amount),0) from retail_order where status='PAID' and cast(created_at as date) = ?", BigDecimal.class, today));
        var todayRefund = nz(db.queryForObject("select coalesce(sum(amount),0) from service_settlement where status='REFUNDED' and cast(refunded_at as date) = ?", BigDecimal.class, today))
            .add(nz(db.queryForObject("select coalesce(sum(amount),0) from retail_order where status='REFUNDED' and cast(created_at as date) = ?", BigDecimal.class, today)));
        result.put("todayServiceRevenue", todayService);
        result.put("todayRetailRevenue", todayRetail);
        result.put("todayRefundAmount", todayRefund);
        result.put("todayRevenue", todayService.add(todayRetail).subtract(todayRefund));
        result.put("unpaidCount", db.queryForObject("select count(*) from grooming g where g.status='WAITING_PICKUP' and not exists (select 1 from service_settlement s where s.grooming_id=g.id and s.status='PAID')", Integer.class));
        result.put("paymentMethods", db.queryForList("select method, count(*) count, coalesce(sum(amount),0) amount from service_settlement where status='PAID' and cast(paid_at as date) = ? group by method", today));
        return ApiResponse.ok(result);
    }

    @GetMapping("/reports")
    public ApiResponse<?> reports() {
        access.require(access.role("ADMIN") || access.role("MANAGER"));
        var report = new LinkedHashMap<String, Object>();
        var serviceRevenue = db.queryForObject("select coalesce(sum(amount),0) from service_settlement where status='PAID'", BigDecimal.class);
        var retailRevenue = db.queryForObject("select coalesce(sum(amount),0) from retail_order where status='PAID'", BigDecimal.class);
        var groomingRefund = db.queryForObject("select coalesce(sum(amount),0) from service_settlement where status='REFUNDED'", BigDecimal.class);
        var orderRefund = db.queryForObject("select coalesce(sum(amount),0) from retail_order where status='REFUNDED'", BigDecimal.class);
        int completedGrooming = db.queryForObject("select count(*) from grooming where status='COMPLETED'", Integer.class);
        int totalOrders = db.queryForObject("select count(*) from retail_order", Integer.class);
        int cardUsageCount = db.queryForObject("select count(*) from prepaid_card_flow where change_times < 0", Integer.class);
        var totalRev = nz(serviceRevenue).add(nz(retailRevenue));
        var totalRef = nz(groomingRefund).add(nz(orderRefund));
        report.put("totalRevenue", totalRev);
        report.put("serviceRevenue", nz(serviceRevenue));
        report.put("retailRevenue", nz(retailRevenue));
        report.put("refundAmount", totalRef);
        report.put("netRevenue", totalRev.subtract(totalRef));
        report.put("completedGroomingCount", completedGrooming);
        report.put("totalRetailOrders", totalOrders);
        report.put("cardUsageCount", cardUsageCount);
        report.put("paymentMethods", db.queryForList("select method, count(*) count, coalesce(sum(amount),0) amount from service_settlement where status='PAID' group by method"));
        return ApiResponse.ok(report);
    }

    private static BigDecimal nz(BigDecimal value) { return value != null ? value : BigDecimal.ZERO; }
}
