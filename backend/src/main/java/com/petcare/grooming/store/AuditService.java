package com.petcare.grooming.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AuditService {
    private final JdbcTemplate db;
    private final Clock clock;

    public AuditService(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public void log(String actionType, String operatorName, String targetType, String targetId, String details) {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            String op = (operatorName != null && !operatorName.isBlank()) ? operatorName : "SYSTEM";
            String dt = details != null && details.length() > 1000 ? details.substring(0, 1000) : details;
            db.update("insert into audit_log(action_type, operator_name, target_type, target_id, details, created_at) values (?,?,?,?,?,?)",
                actionType, op, targetType, targetId, dt, now);
        } catch (Exception ignored) {
            // Audit logging should not crash the primary business transaction if table is temporarily unavailable
        }
    }

    public List<Map<String, Object>> getRecentLogs(int limit) {
        int cappedLimit = Math.min(Math.max(limit, 1), 200);
        return db.queryForList("select * from audit_log order by id desc limit ?", cappedLimit);
    }
}
