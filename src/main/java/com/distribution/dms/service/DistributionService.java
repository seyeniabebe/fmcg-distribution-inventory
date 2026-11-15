package com.distribution.dms.service;

import com.distribution.dms.model.DebtPaymentInstallment;
import com.distribution.dms.model.ProductVariant;
import com.distribution.dms.model.SaleTransaction;
import com.distribution.dms.model.StockMovement;
import com.distribution.dms.repository.DebtPaymentInstallmentRepository;
import com.distribution.dms.repository.ProductVariantRepository;
import com.distribution.dms.repository.SaleTransactionRepository;
import com.distribution.dms.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DistributionService {

    private final ProductVariantRepository variantRepo;
    private final SaleTransactionRepository saleRepo;
    private final StockMovementRepository movementRepo;
    private final DebtPaymentInstallmentRepository installmentRepo;
    private final NotificationService notificationService;

    public DistributionService(ProductVariantRepository variantRepo,
                               SaleTransactionRepository saleRepo,
                               StockMovementRepository movementRepo,
                               DebtPaymentInstallmentRepository installmentRepo,
                               NotificationService notificationService) {
        this.variantRepo = variantRepo;
        this.saleRepo = saleRepo;
        this.movementRepo = movementRepo;
        this.installmentRepo = installmentRepo;
        this.notificationService = notificationService;
    }

    // ── Catalog ───────────────────────────────────────────────────────────────

    public List<ProductVariant> listCatalog() {
        return variantRepo.findAll();
    }

    public Optional<ProductVariant> getVariantBySku(String sku) {
        return variantRepo.findBySku(sku);
    }

    public List<ProductVariant> getVariantsByCategory(String category) {
        return variantRepo.findByCategory(category);
    }

    public List<ProductVariant> getLowStockVariants() {
        return variantRepo.findAll().stream()
            .filter(v -> v.getOnHand() <= v.getReorderMin())
            .toList();
    }

    @Transactional
    public ProductVariant registerVariant(ProductVariant variant) {
        if (variantRepo.findBySku(variant.getSku()).isPresent()) {
            throw new IllegalArgumentException("SKU already exists: " + variant.getSku());
        }
        return variantRepo.save(variant);
    }

    @Transactional
    public void deleteVariant(String sku) {
        ProductVariant v = variantRepo.findBySku(sku)
            .orElseThrow(() -> new IllegalArgumentException("SKU not found: " + sku));

        // Safety rule: Do not delete SKU if historical sales exist
        if (saleRepo.countBySku(sku) > 0) {
            throw new IllegalStateException("Cannot delete SKU " + sku + ": historical sales records depend on it.");
        }

        // Clean up stock movements associated with this SKU if deleting an unsold variant
        movementRepo.deleteBySku(sku);

        variantRepo.delete(v);
    }

    // ── Stock In ─────────────────────────────────────────────────────────────

    @Transactional
    public ProductVariant receiveFactoryStock(String sku, int quantity, String batchNote) {
        return receiveFactoryStock(sku, quantity, batchNote, "Admin (Factory Delivery)");
    }

    @Transactional
    public ProductVariant receiveFactoryStock(String sku, int quantity, String batchNote, String performedBy) {
        ProductVariant v = variantRepo.findBySku(sku)
            .orElseThrow(() -> new IllegalArgumentException("SKU not found: " + sku));
        v.setOnHand(v.getOnHand() + quantity);
        ProductVariant saved = variantRepo.save(v);

        String actor = (performedBy != null && !performedBy.isBlank()) ? performedBy : "Admin";
        String ref = "Factory Receipt • " + v.getSupplier() + " (" + batchNote + ")";
        movementRepo.save(new StockMovement(
            LocalDateTime.now(), "IN", v.getSku(),
            v.getProductLine() + " - " + v.getVariantLabel(),
            quantity, v.getWarehouse(),
            ref,
            actor
        ));

        // Send real-time notification to administrator
        try {
            notificationService.notifyStockIn(actor, v.getSku(), v.getProductLine() + " - " + v.getVariantLabel(), quantity, ref);
        } catch (Exception ignored) {}

        return saved;
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
        boolean isCredit = "CREDIT".equalsIgnoreCase(paymentMethod);
        sale.setCreditCleared(!isCredit);
        sale.setAmountPaid(isCredit ? BigDecimal.ZERO : sale.getTotalAmount());
        sale.setAgentName(agentName);

        String ref = "Agent Sale (" + paymentMethod + ") • " + customerName + " (" + agentName + ")";
        movementRepo.save(new StockMovement(
            LocalDateTime.now(), "OUT", v.getSku(),
            v.getProductLine() + " - " + v.getVariantLabel(),
            quantity, v.getWarehouse(),
            ref,
            agentName
        ));

        SaleTransaction savedSale = saleRepo.save(sale);

        // Send real-time notification to administrator
        try {
            notificationService.notifySale(savedSale);
        } catch (Exception ignored) {}

        return savedSale;
    }

    // ── Debt Installments & Collections ──────────────────────────────────────

    @Transactional
    public SaleTransaction recordCreditPayment(Long saleId, BigDecimal paymentAmount, String paymentMethod, String paymentRefNote) {
        return recordCreditPayment(saleId, paymentAmount, paymentMethod, paymentRefNote, null, null);
    }

    @Transactional
    public SaleTransaction recordCreditPayment(Long saleId, BigDecimal paymentAmount, String paymentMethod,
                                               String paymentRefNote, String screenshotPath, String agentName) {
        SaleTransaction sale = saleRepo.findById(saleId)
            .orElseThrow(() -> new IllegalArgumentException("Sale record not found: " + saleId));

        if (!"CREDIT".equalsIgnoreCase(sale.getPaymentMethod())) {
            throw new IllegalStateException("Sale #" + saleId + " is not a credit sale.");
        }
        if (sale.isCreditCleared()) {
            throw new IllegalStateException("Credit for sale #" + saleId + " is already fully settled.");
        }
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }

        BigDecimal total = sale.getTotalAmount();
        BigDecimal currentPaid = sale.getAmountPaid() != null ? sale.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal remaining = total.subtract(currentPaid);

        if (paymentAmount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException(String.format(
                "Payment of ETB %.2f exceeds remaining balance of ETB %.2f.", paymentAmount, remaining
            ));
        }

        BigDecimal newTotalPaid = currentPaid.add(paymentAmount);
        sale.setAmountPaid(newTotalPaid);
        boolean fullySettled = newTotalPaid.compareTo(total) >= 0;
        if (fullySettled) {
            sale.setCreditCleared(true);
        }

        String method = (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod : "CASH";
        String note = (paymentRefNote != null && !paymentRefNote.isBlank()) ? (" (" + paymentRefNote.trim() + ")") : "";
        String actor = (agentName != null && !agentName.isBlank()) ? agentName : (sale.getAgentName() != null ? sale.getAgentName() : "Agent");
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String installmentLog = String.format(" | Paid: +ETB %.2f via %s on %s by %s%s", paymentAmount, method, dateStr, actor, note);

        sale.setPaymentRef((sale.getPaymentRef() == null ? "" : sale.getPaymentRef()) + installmentLog);
        SaleTransaction savedSale = saleRepo.save(sale);

        BigDecimal remainingAfter = total.subtract(newTotalPaid);
        if (remainingAfter.compareTo(BigDecimal.ZERO) < 0) remainingAfter = BigDecimal.ZERO;

        // Save persistent DebtPaymentInstallment record
        DebtPaymentInstallment installment = new DebtPaymentInstallment(
            saleId,
            sale.getCustomerName(),
            actor,
            paymentAmount,
            method,
            paymentRefNote != null ? paymentRefNote.trim() : "",
            screenshotPath,
            LocalDateTime.now(),
            remainingAfter,
            fullySettled
        );
        DebtPaymentInstallment savedInstallment = installmentRepo.save(installment);

        // Notify Administrator in real-time
        try {
            notificationService.notifyDebtPayment(savedInstallment);
        } catch (Exception ignored) {}

        return savedSale;
    }

    public List<DebtPaymentInstallment> getInstallmentsForSale(Long saleId) {
        return installmentRepo.findBySaleIdOrderByPaidAtAsc(saleId);
    }

    public List<DebtPaymentInstallment> getAllInstallments() {
        return installmentRepo.findAllByOrderByPaidAtDesc();
    }

    @Transactional
    public SaleTransaction clearCreditSale(Long saleId, String settlementNote) {
        SaleTransaction sale = saleRepo.findById(saleId)
            .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        BigDecimal remaining = sale.getRemainingBalance();
        return recordCreditPayment(saleId, remaining, "CASH", "Settled via Admin: " + settlementNote, null, "System Admin");
    }

    @Transactional
    public SaleTransaction attachScreenshot(Long saleId, String screenshotPath) {
        SaleTransaction sale = saleRepo.findById(saleId)
            .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        sale.setPaymentScreenshotPath(screenshotPath);
        return saleRepo.save(sale);
    }

    public List<SaleTransaction> listSales() {
        return saleRepo.findAll();
    }

    // ── Daily Financial Collections ──────────────────────────────────────────

    /**
     * Aggregates real collections: direct cash/mobile sales + credit debt installment payments.
     * Credit sales themselves do NOT count toward collected money on sale date.
     */
    public Map<String, Object> getDailyCollections(LocalDate startDate, LocalDate endDate, String agentFilter, String methodFilter) {
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : LocalDate.now().atTime(23, 59, 59);

        List<SaleTransaction> allSales = saleRepo.findAll();
        List<DebtPaymentInstallment> allInstallments = installmentRepo.findAll();

        BigDecimal totalCash = BigDecimal.ZERO;
        BigDecimal totalMobileBanking = BigDecimal.ZERO;
        BigDecimal totalDebtRepayments = BigDecimal.ZERO;
        List<Map<String, Object>> txList = new ArrayList<>();

        // 1. Direct Sales (CASH and MOBILE_BANKING only; CREDIT collected = 0 on sale date)
        for (SaleTransaction s : allSales) {
            if ("CREDIT".equalsIgnoreCase(s.getPaymentMethod())) {
                continue; // Credit sales are NOT collected money!
            }
            if (s.getSoldAt() == null || s.getSoldAt().isBefore(start) || s.getSoldAt().isAfter(end)) {
                continue;
            }
            if (agentFilter != null && !agentFilter.isBlank() && !agentFilter.equalsIgnoreCase(s.getAgentName())) {
                continue;
            }
            if (methodFilter != null && !methodFilter.isBlank() && !methodFilter.equalsIgnoreCase(s.getPaymentMethod())) {
                continue;
            }

            BigDecimal amt = s.getTotalAmount();
            if ("CASH".equalsIgnoreCase(s.getPaymentMethod())) {
                totalCash = totalCash.add(amt);
            } else if ("MOBILE_BANKING".equalsIgnoreCase(s.getPaymentMethod())) {
                totalMobileBanking = totalMobileBanking.add(amt);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "SALE-" + s.getId());
            item.put("type", "DIRECT_SALE");
            item.put("customerName", s.getCustomerName());
            item.put("agentName", s.getAgentName());
            item.put("timestamp", s.getSoldAt());
            item.put("dateTime", s.getSoldAt());
            item.put("customer", s.getCustomerName());
            item.put("agent", s.getAgentName());
            item.put("paymentMethod", s.getPaymentMethod());
            item.put("amount", amt);
            item.put("reference", s.getPaymentRef());
            item.put("screenshotPath", s.getPaymentScreenshotPath());
            item.put("details", s.getQuantity() + "x " + s.getEffectiveProductName());
            txList.add(item);
        }

        // 2. Debt Installments collected against credit sales
        for (DebtPaymentInstallment inst : allInstallments) {
            if (inst.getPaidAt() == null || inst.getPaidAt().isBefore(start) || inst.getPaidAt().isAfter(end)) {
                continue;
            }
            if (agentFilter != null && !agentFilter.isBlank() && !agentFilter.equalsIgnoreCase(inst.getAgentName())) {
                continue;
            }
            if (methodFilter != null && !methodFilter.isBlank() && !methodFilter.equalsIgnoreCase(inst.getPaymentMethod())) {
                continue;
            }

            BigDecimal amt = inst.getAmount();
            totalDebtRepayments = totalDebtRepayments.add(amt);
            if ("CASH".equalsIgnoreCase(inst.getPaymentMethod())) {
                totalCash = totalCash.add(amt);
            } else if ("MOBILE_BANKING".equalsIgnoreCase(inst.getPaymentMethod())) {
                totalMobileBanking = totalMobileBanking.add(amt);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "INST-" + inst.getId());
            item.put("type", "DEBT_REPAYMENT");
            item.put("customerName", inst.getCustomerName());
            item.put("agentName", inst.getAgentName());
            item.put("timestamp", inst.getPaidAt());
            item.put("dateTime", inst.getPaidAt());
            item.put("customer", inst.getCustomerName());
            item.put("agent", inst.getAgentName());
            item.put("paymentMethod", inst.getPaymentMethod());
            item.put("amount", amt);
            item.put("reference", inst.getPaymentRef());
            item.put("screenshotPath", inst.getPaymentScreenshotPath());
            item.put("details", "Credit Invoice #" + inst.getSaleId() + " (Remaining: ETB " + inst.getRemainingAfter() + ")");
            txList.add(item);
        }

        // Calculate uncollected credit outstanding
        BigDecimal uncollectedCredit = BigDecimal.ZERO;
        for (SaleTransaction s : allSales) {
            if ("CREDIT".equalsIgnoreCase(s.getPaymentMethod()) && !s.isCreditCleared()) {
                if (agentFilter != null && !agentFilter.isBlank() && !agentFilter.equalsIgnoreCase(s.getAgentName())) {
                    continue;
                }
                uncollectedCredit = uncollectedCredit.add(s.getRemainingBalance());
            }
        }

        // Sort chronologically descending
        txList.sort((a, b) -> ((LocalDateTime) b.get("dateTime")).compareTo((LocalDateTime) a.get("dateTime")));

        BigDecimal totalCollected = totalCash.add(totalMobileBanking);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("totalCash", totalCash);
        resp.put("cashCollected", totalCash);
        resp.put("totalMobileBanking", totalMobileBanking);
        resp.put("mobileBankingCollected", totalMobileBanking);
        resp.put("totalDebtRepayments", totalDebtRepayments);
        resp.put("debtInstallmentsCollected", totalDebtRepayments);
        resp.put("totalCollected", totalCollected);
        resp.put("uncollectedCredit", uncollectedCredit);
        resp.put("transactionCount", txList.size());
        resp.put("transactions", txList);
        resp.put("collections", txList);
        return resp;
    }

    // ── Stock Movements & Reports ────────────────────────────────────────────

    public Map<String, Object> getStockMovementsReport(String dateRange, String category, String sku, String type, String agent) {
        LocalDateTime start;
        LocalDateTime now = LocalDateTime.now();
        if ("today".equalsIgnoreCase(dateRange)) {
            start = LocalDate.now().atStartOfDay();
        } else if ("week".equalsIgnoreCase(dateRange)) {
            start = LocalDate.now().minusDays(7).atStartOfDay();
        } else if ("month".equalsIgnoreCase(dateRange)) {
            start = LocalDate.now().minusDays(30).atStartOfDay();
        } else {
            start = LocalDate.now().minusDays(90).atStartOfDay();
        }

        List<StockMovement> movements = movementRepo.findAll();
        List<StockMovement> filtered = movements.stream().filter(m -> {
            if (m.getTimestamp() != null && m.getTimestamp().isBefore(start)) return false;
            if (type != null && !type.isBlank() && !type.equalsIgnoreCase(m.getType())) return false;
            if (sku != null && !sku.isBlank() && !sku.equalsIgnoreCase(m.getSku())) return false;
            if (agent != null && !agent.isBlank()) {
                String p = m.getPerformedBy() != null ? m.getPerformedBy() : m.getReference();
                if (p == null || !p.toLowerCase().contains(agent.toLowerCase())) return false;
            }
            return true;
        }).sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp())).toList();

        int totalReceived = filtered.stream().filter(m -> "IN".equalsIgnoreCase(m.getType())).mapToInt(m -> m.getQuantity() != null ? m.getQuantity() : 0).sum();
        int totalDispatched = filtered.stream().filter(m -> "OUT".equalsIgnoreCase(m.getType())).mapToInt(m -> m.getQuantity() != null ? m.getQuantity() : 0).sum();
        int currentOnHand = variantRepo.findAll().stream().mapToInt(v -> v.getOnHand() != null ? v.getOnHand() : 0).sum();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("totalReceived", totalReceived);
        resp.put("totalIn", totalReceived);
        resp.put("totalDispatched", totalDispatched);
        resp.put("totalOut", totalDispatched);
        resp.put("currentOnHand", currentOnHand);
        resp.put("currentBalance", currentOnHand);
        resp.put("movements", filtered);
        return resp;
    }

    /**
     * Daily Stock In vs Stock Out comparative chart data aggregated from real database stock_movements.
     */
    public List<Map<String, Object>> getDailyStockMovementChart(int days, String sku, String category) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1);
        LocalDateTime start = startDate.atStartOfDay();

        List<StockMovement> movements = movementRepo.findAll();
        Map<LocalDate, int[]> dailyMap = new LinkedHashMap<>();

        // Initialize all days in range
        for (int i = 0; i < days; i++) {
            dailyMap.put(startDate.plusDays(i), new int[]{0, 0}); // [in, out]
        }

        for (StockMovement m : movements) {
            if (m.getTimestamp() == null || m.getTimestamp().isBefore(start)) continue;
            if (sku != null && !sku.isBlank() && !sku.equalsIgnoreCase(m.getSku())) continue;
            LocalDate day = m.getTimestamp().toLocalDate();
            if (dailyMap.containsKey(day)) {
                int qty = m.getQuantity() != null ? m.getQuantity() : 0;
                int[] counts = dailyMap.get(day);
                if ("IN".equalsIgnoreCase(m.getType())) {
                    counts[0] += qty;
                } else if ("OUT".equalsIgnoreCase(m.getType())) {
                    counts[1] += qty;
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd");
        dailyMap.forEach((date, counts) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.toString());
            row.put("label", date.format(fmt));
            row.put("stockIn", counts[0]);
            row.put("stockOut", counts[1]);
            result.add(row);
        });

        return result;
    }

    public List<StockMovement> listMovements() {
        return movementRepo.findAll();
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

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
