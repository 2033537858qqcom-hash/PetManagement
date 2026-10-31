package com.petcare.grooming.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final JdbcTemplate db;
    private final Clock clock;

    public NotificationService(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public void createNotification(String targetRole, Long customerId, String category, String title, String content, String relatedNo, boolean urgent) {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            db.update("insert into notification(target_role, customer_id, category, title, content, related_no, unread, urgent, created_at) values (?,?,?,?,?,?,?,?,?)",
                targetRole, customerId, category, title, content, relatedNo, 1, urgent ? 1 : 0, now);
        } catch (Exception ignored) {
            // Notification delivery should not fail primary business transaction
        }
    }

    public List<Map<String, Object>> getNotificationsForUser(String role, Long customerId) {
        if (role == null) {
            return List.of();
        }
        if ("OWNER".equals(role)) {
            if (customerId != null) {
                return db.queryForList("select * from notification where (target_role = 'OWNER' or target_role = 'ALL') and (customer_id = ? or customer_id is null) order by id desc limit 50", customerId);
            } else {
                return db.queryForList("select * from notification where target_role = 'ALL' order by id desc limit 50");
            }
        } else if ("GROOMER".equals(role)) {
            return db.queryForList("select * from notification where target_role in ('GROOMER', 'STORE', 'ALL') order by id desc limit 50");
        } else if (List.of("MANAGER", "ADMIN", "RECEPTION").contains(role)) {
            return db.queryForList("select * from notification where target_role in ('STORE', 'ALL', 'MANAGER', 'ADMIN', 'RECEPTION') order by id desc limit 50");
        }
        return List.of();
    }

    public boolean markRead(long id, String role, Long customerId) {
        boolean visible = getNotificationsForUser(role, customerId).stream()
            .anyMatch(row -> row.get("id") instanceof Number n && n.longValue() == id);
        if (!visible) return false;
        db.update("update notification set unread = 0 where id = ?", id);
        return true;
    }

    public void markAllRead(String role, Long customerId) {
        if (role == null) return;
        if ("OWNER".equals(role) && customerId != null) {
            db.update("update notification set unread = 0 where (target_role = 'OWNER' or target_role = 'ALL') and (customer_id = ? or customer_id is null)", customerId);
        } else if ("GROOMER".equals(role)) {
            db.update("update notification set unread = 0 where target_role in ('GROOMER', 'STORE', 'ALL')");
        } else if (List.of("MANAGER", "ADMIN", "RECEPTION").contains(role)) {
            db.update("update notification set unread = 0 where target_role in ('STORE', 'ALL', 'MANAGER', 'ADMIN', 'RECEPTION')");
        }
    }
}
