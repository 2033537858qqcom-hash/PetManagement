package com.petcare.grooming.store;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final AccountAccess access;

    public NotificationController(NotificationService notificationService, AccountAccess access) {
        this.notificationService = notificationService;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<?> getNotifications() {
        String role = access.currentRole();
        Long customerId = access.role("OWNER") ? access.customerId() : null;
        return ApiResponse.ok(notificationService.getNotificationsForUser(role, customerId));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<?> markRead(@PathVariable long id) {
        String role = access.currentRole();
        Long customerId = access.role("OWNER") ? access.customerId() : null;
        if (!notificationService.markRead(id, role, customerId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "通知不存在");
        }
        return ApiResponse.ok(Map.of("success", true));
    }

    @PostMapping("/read-all")
    public ApiResponse<?> markAllRead() {
        String role = access.currentRole();
        Long customerId = access.role("OWNER") ? access.customerId() : null;
        notificationService.markAllRead(role, customerId);
        return ApiResponse.ok(Map.of("success", true));
    }
}
