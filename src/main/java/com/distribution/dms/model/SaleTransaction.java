package com.distribution.dms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sale_transactions")
public class SaleTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime soldAt;
    private String agentName;
    private String customerName;
    private String customerPhone;
    private String customerArea;

    @ManyToOne(optional = true)
    @JoinColumn(name = "variant_id", nullable = true)
    private ProductVariant variant;

    private String skuSnapshot;
    private String productLineSnapshot;
    private String variantLabelSnapshot;
    private String categorySnapshot;

    private Integer quantity;
    private BigDecimal unitBuySnapshot;
    private BigDecimal unitSellPrice;
    private String paymentMethod; // CASH, MOBILE_BANKING, CREDIT

    @Column(columnDefinition = "TEXT")
    private String paymentRef;
    private boolean creditCleared;
    private String paymentScreenshotPath; // relative path or filename under ./uploads/screenshots/

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    public SaleTransaction() {}

    @Transient
    public BigDecimal getTotalAmount() {
        if (unitSellPrice == null || quantity == null) return BigDecimal.ZERO;
        return unitSellPrice.multiply(BigDecimal.valueOf(quantity));
    }

    @Transient
    public BigDecimal getRemainingBalance() {
        if (creditCleared) return BigDecimal.ZERO;
        BigDecimal total = getTotalAmount();
        BigDecimal paid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        BigDecimal remaining = total.subtract(paid);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    @Transient
    public BigDecimal getEffectiveAmountPaid() {
        if (creditCleared && (amountPaid == null || amountPaid.compareTo(BigDecimal.ZERO) == 0)) {
            return getTotalAmount();
        }
        return amountPaid != null ? amountPaid : BigDecimal.ZERO;
    }

    @Transient
    public BigDecimal getRealizedProfit() {
        if (unitSellPrice == null || unitBuySnapshot == null || quantity == null) return BigDecimal.ZERO;
        return unitSellPrice.subtract(unitBuySnapshot).multiply(BigDecimal.valueOf(quantity));
    }

    @Transient
    public String getEffectiveSku() {
        return variant != null ? variant.getSku() : skuSnapshot;
    }

    @Transient
    public String getEffectiveProductName() {
        if (variant != null) {
            return variant.getProductLine() + " — " + variant.getVariantLabel();
        }
        String p = productLineSnapshot != null ? productLineSnapshot : "Archived SKU";
        if (variantLabelSnapshot != null && !variantLabelSnapshot.isBlank()) {
            p += " — " + variantLabelSnapshot;
        }
        return p;
    }

    public Long getId() { return id; }
    public LocalDateTime getSoldAt() { return soldAt; }
    public void setSoldAt(LocalDateTime soldAt) { this.soldAt = soldAt; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getCustomerArea() { return customerArea; }
    public void setCustomerArea(String customerArea) { this.customerArea = customerArea; }
    public ProductVariant getVariant() { return variant; }
    public void setVariant(ProductVariant variant) { this.variant = variant; }
    public String getSkuSnapshot() { return skuSnapshot; }
    public void setSkuSnapshot(String skuSnapshot) { this.skuSnapshot = skuSnapshot; }
    public String getProductLineSnapshot() { return productLineSnapshot; }
    public void setProductLineSnapshot(String productLineSnapshot) { this.productLineSnapshot = productLineSnapshot; }
    public String getVariantLabelSnapshot() { return variantLabelSnapshot; }
    public void setVariantLabelSnapshot(String variantLabelSnapshot) { this.variantLabelSnapshot = variantLabelSnapshot; }
    public String getCategorySnapshot() { return categorySnapshot; }
    public void setCategorySnapshot(String categorySnapshot) { this.categorySnapshot = categorySnapshot; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitBuySnapshot() { return unitBuySnapshot; }
    public void setUnitBuySnapshot(BigDecimal unitBuySnapshot) { this.unitBuySnapshot = unitBuySnapshot; }
    public BigDecimal getUnitSellPrice() { return unitSellPrice; }
    public void setUnitSellPrice(BigDecimal unitSellPrice) { this.unitSellPrice = unitSellPrice; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentRef() { return paymentRef; }
    public void setPaymentRef(String paymentRef) { this.paymentRef = paymentRef; }
    public boolean isCreditCleared() { return creditCleared; }
    public void setCreditCleared(boolean creditCleared) { this.creditCleared = creditCleared; }
    public String getPaymentScreenshotPath() { return paymentScreenshotPath; }
    public void setPaymentScreenshotPath(String paymentScreenshotPath) { this.paymentScreenshotPath = paymentScreenshotPath; }
    public BigDecimal getAmountPaid() { return amountPaid != null ? amountPaid : BigDecimal.ZERO; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }
}
