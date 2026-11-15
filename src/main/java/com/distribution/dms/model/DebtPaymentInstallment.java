package com.distribution.dms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "debt_payment_installments")
public class DebtPaymentInstallment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_id", nullable = false)
    private Long saleId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "agent_name", nullable = false)
    private String agentName;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod; // CASH, MOBILE_BANKING

    @Column(name = "payment_ref", columnDefinition = "TEXT")
    private String paymentRef;

    @Column(name = "payment_screenshot_path")
    private String paymentScreenshotPath;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "remaining_after", nullable = false)
    private BigDecimal remainingAfter = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean settled = false;

    public DebtPaymentInstallment() {}

    public DebtPaymentInstallment(Long saleId, String customerName, String agentName,
                                  BigDecimal amount, String paymentMethod, String paymentRef,
                                  String paymentScreenshotPath, LocalDateTime paidAt,
                                  BigDecimal remainingAfter, boolean settled) {
        this.saleId = saleId;
        this.customerName = customerName;
        this.agentName = agentName;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paymentRef = paymentRef;
        this.paymentScreenshotPath = paymentScreenshotPath;
        this.paidAt = paidAt;
        this.remainingAfter = remainingAfter;
        this.settled = settled;
    }

    public Long getId() { return id; }
    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentRef() { return paymentRef; }
    public void setPaymentRef(String paymentRef) { this.paymentRef = paymentRef; }
    public String getPaymentScreenshotPath() { return paymentScreenshotPath; }
    public void setPaymentScreenshotPath(String paymentScreenshotPath) { this.paymentScreenshotPath = paymentScreenshotPath; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public BigDecimal getRemainingAfter() { return remainingAfter; }
    public void setRemainingAfter(BigDecimal remainingAfter) { this.remainingAfter = remainingAfter; }
    public boolean isSettled() { return settled; }
    public void setSettled(boolean settled) { this.settled = settled; }
}
