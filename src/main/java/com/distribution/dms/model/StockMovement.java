package com.distribution.dms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;
    private String type; // IN or OUT
    private String sku;
    private String productName;
    private Integer quantity;
    private String warehouse;
    private String reference;

    @Column(name = "performed_by")
    private String performedBy;

    public StockMovement() {}

    public StockMovement(LocalDateTime timestamp, String type, String sku, String productName,
                         Integer quantity, String warehouse, String reference) {
        this(timestamp, type, sku, productName, quantity, warehouse, reference, null);
    }

    public StockMovement(LocalDateTime timestamp, String type, String sku, String productName,
                         Integer quantity, String warehouse, String reference, String performedBy) {
        this.timestamp = timestamp;
        this.type = type;
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.warehouse = warehouse;
        this.reference = reference;
        this.performedBy = performedBy;
    }

    public Long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getWarehouse() { return warehouse; }
    public void setWarehouse(String warehouse) { this.warehouse = warehouse; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
}
