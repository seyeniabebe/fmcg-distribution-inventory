package com.distribution.dms.controller;

import com.distribution.dms.model.ProductVariant;
import com.distribution.dms.model.SaleTransaction;
import com.distribution.dms.model.StockMovement;
import com.distribution.dms.service.DistributionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DistributionController {
    private final DistributionService service;

    public DistributionController(DistributionService service) {
        this.service = service;
    }

    // ──────────────────────────── HELPER ────────────────────────────

    private boolean isAuthenticated(HttpSession session) {
        return session.getAttribute("role") != null;
    }

    private boolean isAdmin(HttpSession session) {
        return "ADMIN".equals(session.getAttribute("role"));
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
    }

    private Map<String, Object> sanitizeSaleForAgent(SaleTransaction s) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", s.getId());
        map.put("soldAt", s.getSoldAt());
        map.put("agentName", s.getAgentName());
        map.put("customerName", s.getCustomerName());
        map.put("customerPhone", s.getCustomerPhone());
        map.put("customerArea", s.getCustomerArea());
        map.put("quantity", s.getQuantity());
        map.put("unitSellPrice", s.getUnitSellPrice());
        map.put("totalAmount", s.getTotalAmount());
        map.put("paymentMethod", s.getPaymentMethod());
        map.put("paymentRef", s.getPaymentRef());
        map.put("creditCleared", s.isCreditCleared());
        map.put("paymentScreenshotPath", s.getPaymentScreenshotPath());
        map.put("sku", s.getEffectiveSku());
        map.put("productName", s.getEffectiveProductName());
        map.put("skuSnapshot", s.getSkuSnapshot());
        map.put("productLineSnapshot", s.getProductLineSnapshot());
        map.put("variantLabelSnapshot", s.getVariantLabelSnapshot());
        map.put("categorySnapshot", s.getCategorySnapshot());
        if (s.getVariant() != null) {
            map.put("variant", Map.of(
                    "id", s.getVariant().getId(),
                    "sku", s.getVariant().getSku(),
                    "productLine", s.getVariant().getProductLine(),
                    "variantLabel", s.getVariant().getVariantLabel(),
                    "category", s.getVariant().getCategory(),
                    "sellPrice", s.getVariant().getSellPrice(),
                    "onHand", s.getVariant().getOnHand()
            ));
        }
        return map;
    }

    // ──────────────────────────── VARIANTS ────────────────────────────

    /**
     * GET /api/variants
     * ADMIN: returns full variant including buyPrice, unitProfit, buyValuation.
     * AGENT: returns filtered response without buyPrice / unitProfit.
     */
    @GetMapping("/variants")
    public ResponseEntity<?> getVariants(HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        List<ProductVariant> variants = service.listCatalog();
        if (isAdmin(session)) {
            return ResponseEntity.ok(variants);
        }
        // Strip buy price / profit for agents
        List<Map<String, Object>> agentView = variants.stream().map(v -> Map.<String, Object>of(
                "id", v.getId(),
                "sku", v.getSku(),
                "productLine", v.getProductLine(),
                "variantLabel", v.getVariantLabel(),
                "category", v.getCategory(),
                "warehouse", v.getWarehouse(),
                "sellPrice", v.getSellPrice(),
                "onHand", v.getOnHand(),
                "reorderMin", v.getReorderMin()
        )).toList();
        return ResponseEntity.ok(agentView);
    }

    /**
     * POST /api/variants — ADMIN only: register a new SKU
     */
    @PostMapping("/variants")
    public ResponseEntity<?> createVariant(@RequestBody ProductVariant variant, HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        if (!isAdmin(session)) return forbidden();
        variant.setWarehouse("Main Warehouse");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registerVariant(variant));
    }

    /**
     * DELETE /api/variants/{sku} — ADMIN only: delete a SKU from catalog
     */
    @DeleteMapping("/variants/{sku}")
    public ResponseEntity<?> deleteVariant(@PathVariable String sku, HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        if (!isAdmin(session)) return forbidden();
        try {
            service.deleteVariant(sku);
            return ResponseEntity.ok(Map.of("message", "SKU " + sku + " removed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/variants/{sku}/stock-in — ADMIN only: receive factory batch
     */
    @PostMapping("/variants/{sku}/stock-in")
    public ResponseEntity<?> stockIn(@PathVariable String sku,
                                     @RequestBody Map<String, Object> body,
                                     HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        if (!isAdmin(session)) return forbidden();
        int qty = ((Number) body.getOrDefault("quantity", 0)).intValue();
        String note = (String) body.getOrDefault("note", "Factory Batch");
        return ResponseEntity.ok(service.receiveFactoryStock(sku, qty, note));
    }

    // ──────────────────────────── SALES ────────────────────────────

    /**
     * GET /api/sales
     * ADMIN: all sales with full financial profit margins and unit buy snapshots.
     * AGENT: only their own sales, strictly sanitized with buyPrice, unitBuySnapshot,
     * and realizedProfit completely hidden.
     */
    @GetMapping("/sales")
    public ResponseEntity<?> getSales(HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        List<SaleTransaction> all = service.listSales();
        if (isAdmin(session)) {
            return ResponseEntity.ok(all);
        }
        // Agents see only their own sales, strictly sanitized
        String agentFullName = (String) session.getAttribute("fullName");
        List<Map<String, Object>> own = all.stream()
                .filter(s -> agentFullName != null && agentFullName.equals(s.getAgentName()))
                .map(this::sanitizeSaleForAgent)
                .toList();
        return ResponseEntity.ok(own);
    }

    /**
     * POST /api/sales — any authenticated user can log a sale.
     * For agents the agentName is forced to their own fullName.
     * For admins they may specify any agentName (on behalf of).
     */
    @PostMapping("/sales")
    public ResponseEntity<?> recordSale(@RequestBody Map<String, Object> payload,
                                        HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        String role = (String) session.getAttribute("role");
        String agentName;
        if ("ADMIN".equals(role)) {
            // Admin may log on behalf of any agent; fall back to their own fullName
            agentName = payload.containsKey("agentName")
                    ? (String) payload.get("agentName")
                    : (String) session.getAttribute("fullName");
        } else {
            // Agents always use their own identity
            agentName = (String) session.getAttribute("fullName");
        }
        String paymentScreenshotPath = (String) payload.getOrDefault("paymentScreenshotPath", null);
        SaleTransaction sale = service.recordAgentSale(
                (String) payload.get("sku"),
                ((Number) payload.get("quantity")).intValue(),
                (String) payload.getOrDefault("customerName", ""),
                (String) payload.getOrDefault("customerPhone", ""),
                (String) payload.getOrDefault("customerArea", ""),
                (String) payload.getOrDefault("paymentMethod", "CASH"),
                (String) payload.getOrDefault("paymentRef", ""),
                paymentScreenshotPath,
                agentName
        );
        if ("ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.CREATED).body(sale);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(sanitizeSaleForAgent(sale));
    }

    /**
     * POST /api/sales/upload-screenshot — Upload mobile banking receipt image
     * Authenticated agents or admin.
     */
    @PostMapping("/sales/upload-screenshot")
    public ResponseEntity<?> uploadScreenshot(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                              HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }
        try {
            java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads", "screenshots").toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(uploadDir)) {
                java.nio.file.Files.createDirectories(uploadDir);
            }
            String original = file.getOriginalFilename();
            String ext = ".png";
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf(".")).toLowerCase();
            }
            String filename = "pay_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8) + ext;
            java.nio.file.Path targetPath = uploadDir.resolve(filename);
            try (java.io.InputStream in = file.getInputStream()) {
                java.nio.file.Files.copy(in, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            String url = "/api/screenshots/" + filename;
            return ResponseEntity.ok(Map.of(
                    "filename", filename,
                    "url", url
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save screenshot: " + e.getMessage()));
        }
    }

    /**
     * POST /api/sales/{id}/screenshot — Attach/update screenshot on a sale record
     */
    @PostMapping("/sales/{id}/screenshot")
    public ResponseEntity<?> attachScreenshot(@PathVariable Long id,
                                              @RequestBody Map<String, String> body,
                                              HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        String path = body.get("screenshotPath");
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "screenshotPath is required"));
        }
        // If agent, verify they own the sale
        if (!isAdmin(session)) {
            String agentFullName = (String) session.getAttribute("fullName");
            boolean owns = service.listSales().stream()
                    .anyMatch(s -> s.getId().equals(id) && agentFullName != null && agentFullName.equals(s.getAgentName()));
            if (!owns) return forbidden();
        }
        SaleTransaction updated = service.attachScreenshot(id, path);
        if (isAdmin(session)) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.ok(sanitizeSaleForAgent(updated));
    }

    /**
     * GET /api/screenshots/{filename} — Securely serve screenshot image
     */
    @GetMapping("/screenshots/{filename:.+}")
    public ResponseEntity<?> getScreenshot(@PathVariable String filename, HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        // Prevent path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get("uploads", "screenshots").toAbsolutePath().normalize().resolve(filename).normalize();
            if (!java.nio.file.Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            String contentType = java.nio.file.Files.probeContentType(filePath);
            if (contentType == null) contentType = "image/png";

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * POST /api/sales/{id}/clear-credit — ADMIN only
     */
    @PostMapping("/sales/{id}/clear-credit")
    public ResponseEntity<?> clearCredit(@PathVariable Long id,
                                         @RequestBody Map<String, String> body,
                                         HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        if (!isAdmin(session)) return forbidden();
        return ResponseEntity.ok(service.clearCreditSale(id, body.getOrDefault("note", "Settled")));
    }

    // ──────────────────────────── ANALYTICS ────────────────────────────

    /**
     * GET /api/analytics/monthly-profit — ADMIN only
     */
    @GetMapping("/analytics/monthly-profit")
    public ResponseEntity<?> getMonthlyProfit(HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        if (!isAdmin(session)) return forbidden();
        return ResponseEntity.ok(service.getMonthlyProfitData());
    }

    // ──────────────────────────── MOVEMENTS ────────────────────────────

    /**
     * GET /api/movements — ADMIN only
     */
    @GetMapping("/movements")
    public ResponseEntity<?> getMovements(HttpSession session) {
        if (!isAuthenticated(session)) return unauthorized();
        if (!isAdmin(session)) return forbidden();
        return ResponseEntity.ok(service.listMovements());
    }
}
