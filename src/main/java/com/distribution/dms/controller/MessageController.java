package com.distribution.dms.controller;

import com.distribution.dms.model.AppMessage;
import com.distribution.dms.service.MessageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    private boolean isAuthenticated(HttpSession session) {
        return session.getAttribute("username") != null;
    }

    private boolean isPasswordChangeRequired(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute("mustChangePassword"));
    }

    private boolean isAdmin(HttpSession session) {
        return "ADMIN".equals(session.getAttribute("role"));
    }

    private String getUsername(HttpSession session) {
        return (String) session.getAttribute("username");
    }

    private String getRole(HttpSession session) {
        return (String) session.getAttribute("role");
    }

    /** GET /api/messages/conversations — summary of conversations for current user */
    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(HttpSession session) {
        if (!isAuthenticated(session)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        if (isPasswordChangeRequired(session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required", "mustChangePassword", true));

        return ResponseEntity.ok(messageService.getConversationsForUser(getUsername(session), getRole(session)));
    }

    /** GET /api/messages/thread?withUser=username — get message history and mark unread as read */
    @GetMapping("/thread")
    public ResponseEntity<?> getThread(@RequestParam String withUser, HttpSession session) {
        if (!isAuthenticated(session)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        if (isPasswordChangeRequired(session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required", "mustChangePassword", true));

        String currentUser = getUsername(session);
        boolean admin = isAdmin(session);

        // Security check: If agent, they can only view thread with admin
        if (!admin && !"admin".equalsIgnoreCase(withUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Agents can only view messages with System Administrator"));
        }

        List<AppMessage> thread = messageService.getThreadAndMarkRead(currentUser, withUser);
        return ResponseEntity.ok(thread);
    }

    /** POST /api/messages/read?sender=username — mark messages from sender as read */
    @PostMapping("/read")
    public ResponseEntity<?> markMessagesRead(@RequestParam String sender, HttpSession session) {
        if (!isAuthenticated(session)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        if (isPasswordChangeRequired(session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required", "mustChangePassword", true));

        messageService.markAsRead(getUsername(session), sender);
        return ResponseEntity.ok(Map.of("message", "Messages marked as read"));
    }

    /** POST /api/messages/send — send a new message */
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, String> body, HttpSession session) {
        if (!isAuthenticated(session)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        if (isPasswordChangeRequired(session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required", "mustChangePassword", true));

        String recipient = body.getOrDefault("recipientUsername", "").trim();
        String content = body.getOrDefault("content", "").trim();

        if (recipient.isEmpty() || content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "recipientUsername and content are required"));
        }

        String currentUser = getUsername(session);
        boolean admin = isAdmin(session);

        // Security check: If agent, recipient must be admin
        if (!admin && !"admin".equalsIgnoreCase(recipient)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Agents can only send messages to System Administrator"));
        }

        try {
            AppMessage saved = messageService.sendMessage(currentUser, recipient, content);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** GET /api/messages/unread-count — get count of unread messages for logged in user */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(HttpSession session) {
        if (!isAuthenticated(session)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        long count = messageService.getUnreadCount(getUsername(session));
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }
}
