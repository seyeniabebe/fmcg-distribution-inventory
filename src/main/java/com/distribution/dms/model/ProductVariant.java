package com.distribution.dms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sku;

    @Column(nullable = false)
    private String productLine;

    @Column(nullable = false)
    private String variantLabel;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String warehouse;

    private String supplier;

    @Column(nullable = false)
    private BigDecimal buyPrice;

    @Column(nullable = false)
    private BigDecimal sellPrice;

    @Column(nullable = false)
    private Integer onHand;

    @Column(nullable = false)
    private Integer reorderMin;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public ProductVariant() {}

    public ProductVariant(String sku, String productLine, String variantLabel, String category,
                          String warehouse, String supplier, BigDecimal buyPrice, BigDecimal sellPrice,
                          Integer onHand, Integer reorderMin) {
        this.sku = sku;
        this.productLine = productLine;
        this.variantLabel = variantLabel;
        this.category = category;
        this.warehouse = warehouse;
        this.supplier = supplier;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.onHand = onHand;
        this.reorderMin = reorderMin;
    }

    @Transient
    public BigDecimal getUnitProfit() {
        return sellPrice.subtract(buyPrice);
    }

    @Transient
    public BigDecimal getBuyValuation() {
        return buyPrice.multiply(BigDecimal.valueOf(onHand));
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getProductLine() { return productLine; }
    public void setProductLine(String productLine) { this.productLine = productLine; }
    public String getVariantLabel() { return variantLabel; }
    public void setVariantLabel(String variantLabel) { this.variantLabel = variantLabel; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getWarehouse() { return warehouse; }
    public void setWarehouse(String warehouse) { this.warehouse = warehouse; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public void setBuyPrice(BigDecimal buyPrice) { this.buyPrice = buyPrice; }
    public BigDecimal getSellPrice() { return sellPrice; }
    public void setSellPrice(BigDecimal sellPrice) { this.sellPrice = sellPrice; }
    public Integer getOnHand() { return onHand; }
    public void setOnHand(Integer onHand) { this.onHand = onHand; }
    public Integer getReorderMin() { return reorderMin; }
    public void setReorderMin(Integer reorderMin) { this.reorderMin = reorderMin; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
