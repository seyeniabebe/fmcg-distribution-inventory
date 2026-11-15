package com.distribution.dms.service;

import com.distribution.dms.model.AppMessage;
import com.distribution.dms.model.AppUser;
import com.distribution.dms.repository.AppMessageRepository;
import com.distribution.dms.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MessageService {

    private final AppMessageRepository messageRepo;
    private final AppUserRepository userRepo;

    public MessageService(AppMessageRepository messageRepo, AppUserRepository userRepo) {
        this.messageRepo = messageRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public AppMessage sendMessage(String senderUsername, String recipientUsername, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty.");
        }

        AppUser sender = userRepo.findByUsername(senderUsername)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found: " + senderUsername));
        AppUser recipient = userRepo.findByUsername(recipientUsername)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found: " + recipientUsername));

        // Agents can only message Admins
        if ("AGENT".equals(sender.getRole()) && !"ADMIN".equals(recipient.getRole())) {
            throw new IllegalArgumentException("Field agents can only send messages directly to System Administrators.");
        }

        AppMessage msg = new AppMessage(
                sender.getUsername(),
                sender.getFullName(),
                sender.getRole(),
                recipient.getUsername(),
                recipient.getFullName(),
                content.trim(),
                LocalDateTime.now()
        );

        return messageRepo.save(msg);
    }

    @Transactional
    public List<AppMessage> getThreadAndMarkRead(String currentUser, String otherUser) {
        // Mark messages sent by otherUser to currentUser as read
        messageRepo.markThreadAsRead(currentUser, otherUser);
        return messageRepo.findThread(currentUser, otherUser);
    }

    @Transactional
    public void markAsRead(String currentUser, String sender) {
        messageRepo.markThreadAsRead(currentUser, sender);
    }

    public long getUnreadCount(String username) {
        return messageRepo.countByRecipientUsernameAndIsReadFalse(username);
    }

    public List<Map<String, Object>> getConversationsForUser(String username, String role) {
        List<Map<String, Object>> result = new ArrayList<>();

        if ("ADMIN".equals(role)) {
            // Admin sees all agents with their latest message & unread count
            List<AppUser> agents = userRepo.findAll().stream()
                    .filter(u -> "AGENT".equals(u.getRole()))
                    .sorted(Comparator.comparing(AppUser::getFullName))
                    .toList();

            for (AppUser agent : agents) {
                List<AppMessage> thread = messageRepo.findThread(username, agent.getUsername());
                AppMessage lastMsg = thread.isEmpty() ? null : thread.get(thread.size() - 1);
                long unreadFromAgent = messageRepo.countByRecipientUsernameAndSenderUsernameAndIsReadFalse(username, agent.getUsername());

                Map<String, Object> conv = new LinkedHashMap<>();
                conv.put("agentUsername", agent.getUsername());
                conv.put("username", agent.getUsername());
                conv.put("agentFullName", agent.getFullName());
                conv.put("fullName", agent.getFullName());
                conv.put("agentRoute", agent.getRouteName() == null ? "" : agent.getRouteName());
                conv.put("routeName", agent.getRouteName() == null ? "" : agent.getRouteName());
                conv.put("active", agent.isActive());
                conv.put("unreadCount", unreadFromAgent);
                if (lastMsg != null) {
                    conv.put("lastMessage", lastMsg.getContent());
                    conv.put("lastSentAt", lastMsg.getSentAt());
                    conv.put("lastSender", lastMsg.getSenderUsername());
                } else {
                    conv.put("lastMessage", "No messages yet");
                    conv.put("lastSentAt", null);
                    conv.put("lastSender", null);
                }
                result.add(conv);
            }
        } else {
            // Agent sees Admin conversation
            AppUser admin = userRepo.findAll().stream()
                    .filter(u -> "ADMIN".equals(u.getRole()))
                    .findFirst()
                    .orElse(null);

            if (admin != null) {
                List<AppMessage> thread = messageRepo.findThread(username, admin.getUsername());
                AppMessage lastMsg = thread.isEmpty() ? null : thread.get(thread.size() - 1);
                long unreadFromAdmin = messageRepo.countByRecipientUsernameAndSenderUsernameAndIsReadFalse(username, admin.getUsername());

                Map<String, Object> conv = new LinkedHashMap<>();
                conv.put("agentUsername", admin.getUsername());
                conv.put("username", admin.getUsername());
                conv.put("agentFullName", "System Administrator (HQ)");
                conv.put("fullName", "System Administrator (HQ)");
                conv.put("agentRoute", "Central Office");
                conv.put("routeName", "Central Office");
                conv.put("active", true);
                conv.put("unreadCount", unreadFromAdmin);
                if (lastMsg != null) {
                    conv.put("lastMessage", lastMsg.getContent());
                    conv.put("lastSentAt", lastMsg.getSentAt());
                    conv.put("lastSender", lastMsg.getSenderUsername());
                } else {
                    conv.put("lastMessage", "No messages yet");
                    conv.put("lastSentAt", null);
                    conv.put("lastSender", null);
                }
                result.add(conv);
            }
        }

        return result;
    }
}
