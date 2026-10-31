package com.petcare.grooming.store;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {
    private final AuditService auditService;
    private final AccountAccess access;

    public AuditController(AuditService auditService, AccountAccess access) {
        this.auditService = auditService;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<?> getLogs(@RequestParam(defaultValue = "50") int limit) {
        access.require(access.role("ADMIN") || access.role("MANAGER"));
        return ApiResponse.ok(auditService.getRecentLogs(limit));
    }
}
