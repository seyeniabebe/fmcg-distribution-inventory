package com.distribution.dms.controller;

import com.distribution.dms.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private boolean isAuthenticated(HttpSession session) {
        return session.getAttribute("username") != null;
    }

    private boolean isAdmin(HttpSession session) {
        return "ADMIN".equals(session.getAttribute("role"));
    }

    /** GET /api/notifications — list recent 50 notifications (ADMIN only) */
    @GetMapping
    public ResponseEntity<?> getNotifications(HttpSession session) {
        if (!isAuthenticated(session)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        if (!isAdmin(session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));

        return ResponseEntity.ok(notificationService.getRecentNotifications());
    }

    /** GET /api/notifications/unread-count — unread count for badge (ADMIN only) */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(HttpSession session) {
        if (!isAuthenticated(session)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        if (!isAdmin(session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));

        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount()));
    }

    /** POST /api/notifications/{id}/read — mark single notification as read (ADMIN only) */
    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, HttpSession session) {
        if (!isAuthenticated(session)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        if (!isAdmin(session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));

        notificationService.markRead(id);
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    /** POST /api/notifications/mark-all-read — mark all notifications as read (ADMIN only) */
    @PostMapping("/mark-all-read")
    public ResponseEntity<?> markAllRead(HttpSession session) {
        if (!isAuthenticated(session)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        if (!isAdmin(session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));

        notificationService.markAllRead();
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    /** GET /api/notifications/stream — Server-Sent Events stream for real-time notifications (ADMIN only) */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(HttpSession session) {
        if (!isAuthenticated(session) || !isAdmin(session)) {
            SseEmitter errorEmitter = new SseEmitter(0L);
            errorEmitter.completeWithError(new IllegalAccessException("Admin authentication required"));
            return errorEmitter;
        }
        return notificationService.subscribe();
    }
}
