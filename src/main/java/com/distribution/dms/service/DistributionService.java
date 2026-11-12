package com.distribution.dms.service;

import com.distribution.dms.model.ProductVariant;
import com.distribution.dms.model.SaleTransaction;
import com.distribution.dms.model.StockMovement;
import com.distribution.dms.repository.ProductVariantRepository;
import com.distribution.dms.repository.SaleTransactionRepository;
import com.distribution.dms.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DistributionService {
    private final ProductVariantRepository variantRepo;
    private final SaleTransactionRepository saleRepo;
    private final StockMovementRepository movementRepo;

    public DistributionService(ProductVariantRepository variantRepo,
                               SaleTransactionRepository saleRepo,
                               StockMovementRepository movementRepo) {
        this.variantRepo = variantRepo;
        this.saleRepo = saleRepo;
        this.movementRepo = movementRepo;
    }

    // ── Catalog ──────────────────────────────────────────────────────────────

    public List<ProductVariant> listCatalog() {
        return variantRepo.findAll();
    }

    public List<SaleTransaction> listSales() {
        return saleRepo.findAll();
    }

    public List<SaleTransaction> listSalesByAgent(String agentFullName) {
        return saleRepo.findByAgentName(agentFullName);
    }

    public List<StockMovement> listMovements() {
        return movementRepo.findAll();
    }

    // ── Variant CRUD ──────────────────────────────────────────────────────────

    @Transactional
    public ProductVariant registerVariant(ProductVariant variant) {
        ProductVariant saved = variantRepo.save(variant);
        if (saved.getOnHand() != null && saved.getOnHand() > 0) {
            movementRepo.save(new StockMovement(
                LocalDateTime.now(), "IN", saved.getSku(),
                saved.getProductLine() + " - " + saved.getVariantLabel(),
                saved.getOnHand(), saved.getWarehouse(),
                "Initial Factory Stock Registration • " + saved.getSupplier()
            ));
        }
        return saved;
    }

    /**
     * Admin: delete a SKU from the catalog.
     * Preserves sales records for audit/reporting by caching snapshots and unlinking variant FK.
     */
    @Transactional
    public void deleteVariant(String sku) {
        ProductVariant v = variantRepo.findBySku(sku)
            .orElseThrow(() -> new IllegalArgumentException("SKU not found: " + sku));

        // Unlink variant FK on existing sales while preserving audit history
        List<SaleTransaction> linkedSales = saleRepo.findAll().stream()
            .filter(s -> (s.getVariant() != null && sku.equals(s.getVariant().getSku())) || sku.equals(s.getSkuSnapshot()))
            .toList();
        for (SaleTransaction s : linkedSales) {
            if (s.getSkuSnapshot() == null && s.getVariant() != null) {
                s.setSkuSnapshot(s.getVariant().getSku());
                s.setProductLineSnapshot(s.getVariant().getProductLine());
                s.setVariantLabelSnapshot(s.getVariant().getVariantLabel());
                s.setCategorySnapshot(s.getVariant().getCategory());
            }
            s.setVariant(null);
            saleRepo.save(s);
        }

        // Delete stock movements for this SKU
        movementRepo.findAll().stream()
            .filter(m -> sku.equals(m.getSku()))
            .forEach(movementRepo::delete);

        variantRepo.delete(v);
    }

    // ── Stock In ─────────────────────────────────────────────────────────────

    @Transactional
    public ProductVariant receiveFactoryStock(String sku, int quantity, String batchNote) {
        ProductVariant v = variantRepo.findBySku(sku)
            .orElseThrow(() -> new IllegalArgumentException("SKU not found: " + sku));
        v.setOnHand(v.getOnHand() + quantity);
        movementRepo.save(new StockMovement(
            LocalDateTime.now(), "IN", v.getSku(),
            v.getProductLine() + " - " + v.getVariantLabel(),
            quantity, v.getWarehouse(),
            "Factory Receipt • " + v.getSupplier() + " (" + batchNote + ")"
        ));
        return variantRepo.save(v);
    }

    // ── Sales ─────────────────────────────────────────────────────────────────

    @Transactional
    public SaleTransaction recordAgentSale(String sku, int quantity, String customerName,
                                           String customerPhone, String customerArea,
                                           String paymentMethod, String paymentRef, String agentName) {
        return recordAgentSale(sku, quantity, customerName, customerPhone, customerArea, paymentMethod, paymentRef, null, agentName);
    }

    @Transactional
    public SaleTransaction recordAgentSale(String sku, int quantity, String customerName,
                                           String customerPhone, String customerArea,
                                           String paymentMethod, String paymentRef,
                                           String paymentScreenshotPath, String agentName) {
        ProductVariant v = variantRepo.findBySku(sku)
            .orElseThrow(() -> new IllegalArgumentException("SKU not found: " + sku));
        if (v.getOnHand() < quantity) {
            throw new IllegalStateException("Insufficient stock for SKU " + sku
                + " (requested " + quantity + ", available " + v.getOnHand() + ")");
        }
        v.setOnHand(v.getOnHand() - quantity);
        variantRepo.save(v);

        SaleTransaction sale = new SaleTransaction();
        sale.setSoldAt(LocalDateTime.now());
        sale.setVariant(v);
        sale.setSkuSnapshot(v.getSku());
        sale.setProductLineSnapshot(v.getProductLine());
        sale.setVariantLabelSnapshot(v.getVariantLabel());
        sale.setCategorySnapshot(v.getCategory());
        sale.setQuantity(quantity);
        sale.setUnitBuySnapshot(v.getBuyPrice());
        sale.setUnitSellPrice(v.getSellPrice());
        sale.setCustomerName(customerName);
        sale.setCustomerPhone(customerPhone);
        sale.setCustomerArea(customerArea);
        sale.setPaymentMethod(paymentMethod);
        sale.setPaymentRef(paymentRef);
        sale.setPaymentScreenshotPath(paymentScreenshotPath);
        sale.setCreditCleared(!"CREDIT".equalsIgnoreCase(paymentMethod));
        sale.setAgentName(agentName);

        movementRepo.save(new StockMovement(
            LocalDateTime.now(), "OUT", v.getSku(),
            v.getProductLine() + " - " + v.getVariantLabel(),
            quantity, v.getWarehouse(),
            "Agent Sale (" + paymentMethod + ") • " + customerName + " (" + agentName + ")"
        ));
        return saleRepo.save(sale);
    }

    @Transactional
    public SaleTransaction clearCreditSale(Long saleId, String settlementNote) {
        SaleTransaction sale = saleRepo.findById(saleId)
            .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        sale.setCreditCleared(true);
        sale.setPaymentRef((sale.getPaymentRef() == null ? "" : sale.getPaymentRef())
            + " | Cleared: " + settlementNote);
        return saleRepo.save(sale);
    }

    @Transactional
    public SaleTransaction attachScreenshot(Long saleId, String screenshotPath) {
        SaleTransaction sale = saleRepo.findById(saleId)
            .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        sale.setPaymentScreenshotPath(screenshotPath);
        return saleRepo.save(sale);
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    /**
     * Returns a list of { year, month, monthLabel, profit, revenue, unitsSold } maps, one per calendar month
     * that has at least one sale, ordered chronologically.
     */
    public List<Map<String, Object>> getMonthlyProfitData() {
        List<Object[]> rows = saleRepo.findMonthlyProfitNative();
        String[] MONTH_NAMES = {"", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                     "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        return rows.stream().map(row -> {
            int yr  = ((Number) row[0]).intValue();
            int mo  = ((Number) row[1]).intValue();
            double profit  = row[2] == null ? 0.0 : ((Number) row[2]).doubleValue();
            double revenue = row.length > 3 && row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            int units      = row.length > 4 && row[4] != null ? ((Number) row[4]).intValue() : 0;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("year",       yr);
            m.put("month",      mo);
            m.put("monthLabel", MONTH_NAMES[mo] + " " + yr);
            m.put("profit",     Math.round(profit * 100.0) / 100.0);
            m.put("revenue",    Math.round(revenue * 100.0) / 100.0);
            m.put("unitsSold",  units);
            return m;
        }).toList();
    }
}
