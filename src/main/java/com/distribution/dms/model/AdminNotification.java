package com.distribution.dms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_notifications")
public class AdminNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_type", nullable = false)
    private String activityType; // SALE_RECORDED, STOCK_IN, DEBT_PAYMENT

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "agent_name")
    private String agentName;

    @Column(name = "product_info")
    private String productInfo;

    private Integer quantity;

    private BigDecimal amount;

    @Column(name = "customer_name")
    private String customerName;

    private String reference;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    public AdminNotification() {}

    public AdminNotification(String activityType, String title, String message, String agentName,
                             String productInfo, Integer quantity, BigDecimal amount,
                             String customerName, String reference, LocalDateTime createdAt) {
        this.activityType = activityType;
        this.title = title;
        this.message = message;
        this.agentName = agentName;
        this.productInfo = productInfo;
        this.quantity = quantity;
        this.amount = amount;
        this.customerName = customerName;
        this.reference = reference;
        this.createdAt = createdAt;
        this.isRead = false;
    }

    public Long getId() { return id; }
    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getProductInfo() { return productInfo; }
    public void setProductInfo(String productInfo) { this.productInfo = productInfo; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}
