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

    public StockMovement() {}

    public StockMovement(LocalDateTime timestamp, String type, String sku, String productName,
                         Integer quantity, String warehouse, String reference) {
        this.timestamp = timestamp;
        this.type = type;
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.warehouse = warehouse;
        this.reference = reference;
    }

    public Long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public Integer getQuantity() { return quantity; }
    public String getWarehouse() { return warehouse; }
    public String getReference() { return reference; }
}
