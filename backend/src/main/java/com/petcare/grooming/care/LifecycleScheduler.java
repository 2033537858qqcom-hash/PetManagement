package com.petcare.grooming.care;

import com.petcare.grooming.inventory.InventoryService;
import com.petcare.grooming.store.StoreConfigService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class LifecycleScheduler {
    private final JdbcTemplate db;
    private final Clock clock;
    private final InventoryService inventory;
    private final StoreConfigService storeConfig;

    public LifecycleScheduler(JdbcTemplate db, Clock clock, InventoryService inventory, StoreConfigService storeConfig) {
        this.db = db;
        this.clock = clock;
        this.inventory = inventory;
        this.storeConfig = storeConfig;
    }

    /**
     * Marks overdue CONFIRMED appointments as NO_SHOW when store closes or date has passed.
     */
    @Scheduled(cron = "0 30 21 * * *")
    @Transactional
    public int markOverdueNoShows() {
        db.queryForObject("select id from booking_lock where id=1 for update", Integer.class);
        LocalDate today = LocalDate.now(clock);
        LocalTime nowTime = LocalTime.now(clock);
        if (nowTime.isAfter(storeConfig.getCloseTime())) {
            return db.update("update appointment set status='NO_SHOW' where status='CONFIRMED' and appointment_date<=?", today);
        }
        return db.update("update appointment set status='NO_SHOW' where status='CONFIRMED' and appointment_date<?", today);
    }

    /**
     * Cancels unpaid retail orders older than 30 minutes and restores their locked stock.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    @Transactional
    public int releaseExpiredUnpaidOrders() {
        db.queryForObject("select id from sale_lock where id=1 for update", Integer.class);
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(30);
        var expired = db.queryForList("select * from retail_order where status='SUBMITTED' and created_at<? for update", cutoff);
        for (var order : expired) {
            long orderId = ((Number) order.get("id")).longValue();
            long productId = ((Number) order.get("product_id")).longValue();
            int stockUnits = ((Number) order.get("stock_units")).intValue();
            BigDecimal amount = (BigDecimal) order.get("amount");
            inventory.restoreSale(productId, stockUnits, orderId, "CANCEL");
            db.update("update retail_order set status='CANCELLED' where id=?", orderId);
            db.update("insert into retail_order_event(order_id,action,amount,reason,operator_name,created_at) values (?,?,?,?,?,?)",
                orderId, "CANCELLED", amount, "超时未支付自动释放库存", "system", LocalDateTime.now(clock));
        }
        return expired.size();
    }
}
