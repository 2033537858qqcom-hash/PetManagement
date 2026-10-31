package com.petcare.grooming.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StoreConfigService {
    private final JdbcTemplate db;
    private final Clock clock;

    public StoreConfigService(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public Map<String, Object> getSettings() {
        var rows = db.queryForList("select * from store_settings where id = 1");
        if (rows.isEmpty()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("id", 1);
            fallback.put("storeName", "PAW & PURE 爪印轻奢宠护中心（旗舰店）");
            fallback.put("businessHours", "09:00 - 21:00");
            fallback.put("openTime", "09:00");
            fallback.put("closeTime", "21:00");
            fallback.put("bufferMinutes", 15);
            fallback.put("address", "上海市徐汇区武康路 288 号 Apple Park 创意街区 1F");
            fallback.put("phone", "400-820-8899");
            fallback.put("updatedAt", LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            return fallback;
        }
        var row = rows.get(0);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", row.get("id"));
        res.put("storeName", row.get("store_name"));
        res.put("businessHours", row.get("business_hours"));
        res.put("openTime", row.get("open_time"));
        res.put("closeTime", row.get("close_time"));
        res.put("bufferMinutes", row.get("buffer_minutes"));
        res.put("address", row.get("address"));
        res.put("phone", row.get("phone"));
        Object ts = row.get("updated_at");
        res.put("updatedAt", ts != null ? ts.toString() : null);
        return res;
    }

    public LocalTime getOpenTime() {
        try {
            var rows = db.queryForList("select open_time from store_settings where id = 1");
            if (!rows.isEmpty() && rows.get(0).get("open_time") != null) {
                return LocalTime.parse(rows.get(0).get("open_time").toString().trim());
            }
        } catch (Exception ignored) {}
        return LocalTime.of(9, 0);
    }

    public LocalTime getCloseTime() {
        try {
            var rows = db.queryForList("select close_time from store_settings where id = 1");
            if (!rows.isEmpty() && rows.get(0).get("close_time") != null) {
                return LocalTime.parse(rows.get(0).get("close_time").toString().trim());
            }
        } catch (Exception ignored) {}
        return LocalTime.of(21, 0);
    }

    public int getBufferMinutes() {
        try {
            var rows = db.queryForList("select buffer_minutes from store_settings where id = 1");
            if (!rows.isEmpty() && rows.get(0).get("buffer_minutes") instanceof Number n) {
                return n.intValue();
            }
        } catch (Exception ignored) {}
        return 15;
    }

    public void updateSettings(String storeName, String businessHours, String openTime, String closeTime, int bufferMinutes, String address, String phone) {
        LocalDateTime now = LocalDateTime.now(clock);
        int updated = db.update("update store_settings set store_name=?, business_hours=?, open_time=?, close_time=?, buffer_minutes=?, address=?, phone=?, updated_at=? where id = 1",
            storeName, businessHours, openTime, closeTime, bufferMinutes, address, phone, now);
        if (updated == 0) {
            db.update("insert into store_settings (id, store_name, business_hours, open_time, close_time, buffer_minutes, address, phone, updated_at) values (1, ?, ?, ?, ?, ?, ?, ?, ?)",
                storeName, businessHours, openTime, closeTime, bufferMinutes, address, phone, now);
        }
    }
}
