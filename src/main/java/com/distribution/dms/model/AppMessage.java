package com.distribution.dms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_messages")
public class AppMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_username", nullable = false)
    private String senderUsername;

    @Column(name = "sender_full_name", nullable = false)
    private String senderFullName;

    @Column(name = "sender_role", nullable = false)
    private String senderRole; // ADMIN, AGENT

    @Column(name = "recipient_username", nullable = false)
    private String recipientUsername;

    @Column(name = "recipient_full_name", nullable = false)
    private String recipientFullName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    public AppMessage() {}

    public AppMessage(String senderUsername, String senderFullName, String senderRole,
                      String recipientUsername, String recipientFullName, String content,
                      LocalDateTime sentAt) {
        this.senderUsername = senderUsername;
        this.senderFullName = senderFullName;
        this.senderRole = senderRole;
        this.recipientUsername = recipientUsername;
        this.recipientFullName = recipientFullName;
        this.content = content;
        this.sentAt = sentAt;
        this.isRead = false;
    }

    public Long getId() { return id; }
    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getSenderFullName() { return senderFullName; }
    public void setSenderFullName(String senderFullName) { this.senderFullName = senderFullName; }
    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }
    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }
    public String getRecipientFullName() { return recipientFullName; }
    public void setRecipientFullName(String recipientFullName) { this.recipientFullName = recipientFullName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}
