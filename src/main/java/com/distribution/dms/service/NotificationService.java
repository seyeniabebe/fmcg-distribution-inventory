package com.distribution.dms.service;

import com.distribution.dms.model.AdminNotification;
import com.distribution.dms.model.DebtPaymentInstallment;
import com.distribution.dms.model.SaleTransaction;
import com.distribution.dms.repository.AdminNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final AdminNotificationRepository notificationRepo;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public NotificationService(AdminNotificationRepository notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(emitter));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(java.util.Map.of("status", "CONNECTED", "unreadCount", notificationRepo.countByIsReadFalse())));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    @Transactional
    public AdminNotification notifySale(SaleTransaction sale) {
        String agent = sale.getAgentName() != null ? sale.getAgentName() : "Field Agent";
        String customer = sale.getCustomerName() != null ? sale.getCustomerName() : "Retail Customer";
        String product = sale.getEffectiveProductName();
        int qty = sale.getQuantity() != null ? sale.getQuantity() : 0;
        BigDecimal amount = sale.getTotalAmount();
        String method = sale.getPaymentMethod() != null ? sale.getPaymentMethod() : "CASH";

        String title = "New Sale Recorded: " + customer;
        String message = String.format("%s sold %d cartons/bales of %s to %s for ETB %.2f (%s)",
                agent, qty, product, customer, amount, method);

        AdminNotification notif = new AdminNotification(
                "SALE_RECORDED",
                title,
                message,
                agent,
                product,
                qty,
                amount,
                customer,
                "SALE-" + sale.getId(),
                LocalDateTime.now()
        );

        AdminNotification saved = notificationRepo.save(notif);
        broadcastNotification(saved);
        return saved;
    }

    @Transactional
    public AdminNotification notifyStockIn(String agentName, String sku, String productName, int quantity, String reference) {
        String agent = (agentName != null && !agentName.isBlank()) ? agentName : "Agent";
        String title = "Warehouse Stock-In: +" + quantity + " units";
        String message = String.format("%s received %d units of %s (%s) into warehouse. Ref: %s",
                agent, quantity, productName, sku, reference);

        AdminNotification notif = new AdminNotification(
                "STOCK_IN",
                title,
                message,
                agent,
                sku + " - " + productName,
                quantity,
                null,
                null,
                reference,
                LocalDateTime.now()
        );

        AdminNotification saved = notificationRepo.save(notif);
        broadcastNotification(saved);
        return saved;
    }

    @Transactional
    public AdminNotification notifyDebtPayment(DebtPaymentInstallment installment) {
        String title = installment.isSettled()
                ? "Debt Fully Cleared: " + installment.getCustomerName()
                : "Debt Installment Received: " + installment.getCustomerName();

        String message = String.format("%s collected ETB %.2f (%s) from %s. Remaining: ETB %.2f",
                installment.getAgentName(), installment.getAmount(), installment.getPaymentMethod(),
                installment.getCustomerName(), installment.getRemainingAfter());

        AdminNotification notif = new AdminNotification(
                "DEBT_PAYMENT",
                title,
                message,
                installment.getAgentName(),
                "Sale #" + installment.getSaleId(),
                null,
                installment.getAmount(),
                installment.getCustomerName(),
                installment.getPaymentRef(),
                LocalDateTime.now()
        );

        AdminNotification saved = notificationRepo.save(notif);
        broadcastNotification(saved);
        return saved;
    }

    private void broadcastNotification(AdminNotification notif) {
        long unread = notificationRepo.countByIsReadFalse();
        java.util.Map<String, Object> payload = java.util.Map.of(
                "notification", notif,
                "unreadCount", unread
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("NOTIFICATION")
                        .data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    public List<AdminNotification> getRecentNotifications() {
        return notificationRepo.findTop50ByOrderByCreatedAtDesc();
    }

    public long getUnreadCount() {
        return notificationRepo.countByIsReadFalse();
    }

    @Transactional
    public void markAllRead() {
        notificationRepo.markAllAsRead();
    }

    @Transactional
    public void markRead(Long id) {
        notificationRepo.markAsRead(id);
    }
}
