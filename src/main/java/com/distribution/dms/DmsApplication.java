package com.distribution.dms;

import com.distribution.dms.model.AppUser;
import com.distribution.dms.model.ProductVariant;
import com.distribution.dms.model.SaleTransaction;
import com.distribution.dms.model.StockMovement;
import com.distribution.dms.repository.AppUserRepository;
import com.distribution.dms.repository.ProductVariantRepository;
import com.distribution.dms.repository.SaleTransactionRepository;
import com.distribution.dms.repository.StockMovementRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.stream.Collectors;

@SpringBootApplication
public class DmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(DmsApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = false)
    CommandLineRunner seedData(ProductVariantRepository variantRepo,
                              AppUserRepository userRepo,
                              SaleTransactionRepository saleRepo,
                              StockMovementRepository movementRepo,
                              BCryptPasswordEncoder passwordEncoder) {
        return args -> {
            // ── Seed product catalog (all in single "Main Warehouse") ──
            if (variantRepo.count() == 0) {
                variantRepo.saveAll(List.of(
                    new ProductVariant("DIA-S1", "ComfortDry Baby Diapers", "Size 1 (Newborn, 2-5 kg)",  "Diapers",       "Main Warehouse", "Addis Hygiene Factory",    new BigDecimal("380"),  new BigDecimal("460"),  85,  25),
                    new ProductVariant("DIA-S2", "ComfortDry Baby Diapers", "Size 2 (Mini, 3-6 kg)",     "Diapers",       "Main Warehouse", "Addis Hygiene Factory",    new BigDecimal("400"),  new BigDecimal("490"),  64,  25),
                    new ProductVariant("DIA-S3", "ComfortDry Baby Diapers", "Size 3 (Midi, 4-9 kg)",     "Diapers",       "Main Warehouse", "Addis Hygiene Factory",    new BigDecimal("430"),  new BigDecimal("530"),  18,  30),
                    new ProductVariant("DIA-S4", "ComfortDry Baby Diapers", "Size 4 (Maxi, 7-18 kg)",    "Diapers",       "Main Warehouse", "Addis Hygiene Factory",    new BigDecimal("460"),  new BigDecimal("570"),  30,  25),
                    new ProductVariant("DIA-S5", "ComfortDry Baby Diapers", "Size 5 (Junior, 11-25 kg)", "Diapers",       "Main Warehouse", "Addis Hygiene Factory",    new BigDecimal("490"),  new BigDecimal("610"),  42,  20),
                    new ProductVariant("PAD-001", "SoftCare Sanitary Pads",  "Single Variant (Standard 10-Pad Pack, Carton of 24)", "Sanitary Pads", "Main Warehouse", "Awash Sanitary Products", new BigDecimal("720"), new BigDecimal("900"), 110, 35),
                    new ProductVariant("WIP-080", "PureTouch Baby Wet Wipes","80-Sheet Flip-Top Pack (Box of 12)",  "Wet Wipes",    "Main Warehouse", "Addis Hygiene Factory",    new BigDecimal("540"),  new BigDecimal("680"),  55,  20),
                    new ProductVariant("WIP-120", "PureTouch Aloe Wet Wipes","120-Sheet Value Pack (Box of 12)",    "Wet Wipes",    "Main Warehouse", "Addis Hygiene Factory",    new BigDecimal("690"),  new BigDecimal("860"),  20,  15),
                    new ProductVariant("TIS-10R", "CloudSoft 2-Ply Toilet Tissue", "10-Roll Multipack Bale",        "Tissue Paper", "Main Warehouse", "Kombolcha Paper Mill",     new BigDecimal("310"),  new BigDecimal("395"), 140,  40),
                    new ProductVariant("TIS-200", "CloudSoft Facial Tissue",       "200-Sheet Box (Carton of 24)",  "Tissue Paper", "Main Warehouse", "Kombolcha Paper Mill",     new BigDecimal("820"), new BigDecimal("1020"),  20,  15)
                ));
            }

            // ── Seed user accounts (BCrypt-hashed passwords for fresh installs) ──
            if (userRepo.count() == 0) {
                userRepo.saveAll(List.of(
                    new AppUser("admin",   passwordEncoder.encode("admin123"),   "System Administrator",      "ADMIN", null),
                    new AppUser("dawit",   passwordEncoder.encode("dawit123"),   "Dawit Mekonnen",            "AGENT", "Bole & CMC Route"),
                    new AppUser("selam",   passwordEncoder.encode("selam123"),   "Selamawit Tadesse",         "AGENT", "Merkato & Piassa Route"),
                    new AppUser("biniyam", passwordEncoder.encode("biniyam123"), "Biniyam Alemu",             "AGENT", "Megenagna & Ayat Route")
                ));
            }

            // ── Seed initial stock movements if empty ──
            if (movementRepo.count() == 0) {
                for (ProductVariant v : variantRepo.findAll()) {
                    movementRepo.save(new StockMovement(
                        LocalDateTime.now().minusMonths(6),
                        "IN", v.getSku(),
                        v.getProductLine() + " — " + v.getVariantLabel(),
                        v.getOnHand() + 80,
                        v.getWarehouse(),
                        "Initial Factory Batch Delivery • " + v.getSupplier()
                    ));
                }
            }

            // ── Seed realistic multi-month sales for profit trend & debtor tracking ──
            if (saleRepo.count() == 0) {
                Map<String, ProductVariant> vMap = variantRepo.findAll().stream()
                    .collect(Collectors.toMap(ProductVariant::getSku, p -> p));

                recordSeedSale(saleRepo, movementRepo, vMap.get("DIA-S1"), 25, "Abebe Wholesale & Mini Market", "0912-345-678", "Merkato Gojam Berenda", "CASH", "Cash payment on delivery", null, true, "Selamawit Tadesse", LocalDateTime.of(2026, 5, 12, 11, 30));
                recordSeedSale(saleRepo, movementRepo, vMap.get("PAD-001"), 15, "Shoa Supermarket (Bole)", "0911-234-567", "Bole Medhanialem", "MOBILE_BANKING", "Telebirr transfer TB-10492", null, true, "Dawit Mekonnen", LocalDateTime.of(2026, 5, 24, 15, 10));

                recordSeedSale(saleRepo, movementRepo, vMap.get("DIA-S2"), 30, "Safeway Supermarket (Megenagna)", "0913-456-789", "Megenagna Square", "CASH", "Cash handed over", null, true, "Biniyam Alemu", LocalDateTime.of(2026, 6, 8, 10, 15));
                recordSeedSale(saleRepo, movementRepo, vMap.get("WIP-080"), 25, "Queens Supermarket (CMC)", "0911-876-543", "CMC Michael", "MOBILE_BANKING", "CBE Birr Ref #882910", null, true, "Dawit Mekonnen", LocalDateTime.of(2026, 6, 19, 14, 45));
                recordSeedSale(saleRepo, movementRepo, vMap.get("TIS-10R"), 35, "Selam Family Pharmacy & Mart", "0914-567-890", "Piassa Churchill Rd", "CREDIT", "Net 15 settled via bank", null, true, "Selamawit Tadesse", LocalDateTime.of(2026, 6, 28, 16, 20));

                recordSeedSale(saleRepo, movementRepo, vMap.get("DIA-S3"), 20, "Abebe Wholesale & Mini Market", "0912-345-678", "Merkato Gojam Berenda", "CASH", "Cash on delivery", null, true, "Selamawit Tadesse", LocalDateTime.of(2026, 7, 5, 9, 30));
                recordSeedSale(saleRepo, movementRepo, vMap.get("PAD-001"), 25, "Shoa Supermarket (Bole)", "0911-234-567", "Bole Medhanialem", "MOBILE_BANKING", "Telebirr Ref TB-773829", null, true, "Dawit Mekonnen", LocalDateTime.of(2026, 7, 16, 13, 0));
                recordSeedSale(saleRepo, movementRepo, vMap.get("WIP-120"), 15, "Queens Supermarket (CMC)", "0911-876-543", "CMC Michael", "CREDIT", "Cleared in full", null, true, "Dawit Mekonnen", LocalDateTime.of(2026, 7, 27, 15, 40));

                recordSeedSale(saleRepo, movementRepo, vMap.get("DIA-S4"), 25, "Safeway Supermarket (Megenagna)", "0913-456-789", "Megenagna Square", "CASH", "Cash collection", null, true, "Biniyam Alemu", LocalDateTime.of(2026, 8, 4, 11, 20));
                recordSeedSale(saleRepo, movementRepo, vMap.get("TIS-200"), 20, "Edna Mall Mini Mart", "0911-998-877", "Bole Road", "MOBILE_BANKING", "CBE transfer CBE-33921", null, true, "Dawit Mekonnen", LocalDateTime.of(2026, 8, 14, 16, 15));
                recordSeedSale(saleRepo, movementRepo, vMap.get("DIA-S1"), 30, "Abebe Wholesale & Mini Market", "0912-345-678", "Merkato Gojam Berenda", "CREDIT", "Cleared 30 days", null, true, "Selamawit Tadesse", LocalDateTime.of(2026, 8, 22, 10, 50));

                recordSeedSale(saleRepo, movementRepo, vMap.get("PAD-001"), 30, "Queens Supermarket (CMC)", "0911-876-543", "CMC Michael", "MOBILE_BANKING", "Telebirr transfer TB-992810", null, true, "Dawit Mekonnen", LocalDateTime.of(2026, 9, 6, 14, 0));
                recordSeedSale(saleRepo, movementRepo, vMap.get("DIA-S5"), 20, "Safeway Supermarket (Megenagna)", "0913-456-789", "Megenagna Square", "CASH", "Cash collection", null, true, "Biniyam Alemu", LocalDateTime.of(2026, 9, 15, 10, 30));
                recordSeedSale(saleRepo, movementRepo, vMap.get("WIP-080"), 30, "Selam Family Pharmacy & Mart", "0914-567-890", "Piassa Churchill Rd", "CASH", "Direct van sale", null, true, "Selamawit Tadesse", LocalDateTime.of(2026, 9, 23, 12, 10));
                recordSeedSale(saleRepo, movementRepo, vMap.get("TIS-10R"), 40, "Abebe Wholesale & Mini Market", "0912-345-678", "Merkato Gojam Berenda", "CREDIT", "Settled after inventory count", null, true, "Selamawit Tadesse", LocalDateTime.of(2026, 9, 29, 15, 0));

                // October sales (current month) including sample Mobile Banking receipts!
                recordSeedSale(saleRepo, movementRepo, vMap.get("DIA-S2"), 25, "Abebe Wholesale & Mini Market", "0912-345-678", "Merkato Gojam Berenda", "CASH", "Van delivery cash", null, true, "Selamawit Tadesse", LocalDateTime.of(2026, 10, 5, 11, 40));
                recordSeedSale(saleRepo, movementRepo, vMap.get("DIA-S1"), 30, "Shoa Supermarket (Bole)", "0911-234-567", "Bole Medhanialem", "MOBILE_BANKING", "TB-20261015-8849201", "pay_sample_telebirr.png", true, "Dawit Mekonnen", LocalDateTime.of(2026, 10, 15, 14, 32));
                recordSeedSale(saleRepo, movementRepo, vMap.get("PAD-001"), 20, "Queens Supermarket (CMC)", "0911-876-543", "CMC Michael", "MOBILE_BANKING", "CBE-TX-9938210492", "pay_sample_cbe.png", true, "Dawit Mekonnen", LocalDateTime.of(2026, 10, 18, 10, 15));
                recordSeedSale(saleRepo, movementRepo, vMap.get("DIA-S3"), 12, "Safeway Supermarket (Megenagna)", "0913-456-789", "Megenagna Square", "CREDIT", "15-day credit terms, pending check", null, false, "Biniyam Alemu", LocalDateTime.of(2026, 10, 19, 16, 20));
                recordSeedSale(saleRepo, movementRepo, vMap.get("WIP-120"), 10, "Selam Family Pharmacy & Mart", "0914-567-890", "Piassa Churchill Rd", "CREDIT", "Delivery note signed, due end of month", null, false, "Selamawit Tadesse", LocalDateTime.of(2026, 10, 21, 14, 0));
            }
        };
    }

    private void recordSeedSale(SaleTransactionRepository saleRepo,
                                StockMovementRepository movementRepo,
                                ProductVariant v, int qty, String cust, String phone,
                                String area, String pm, String ref, String screenshot,
                                boolean cleared, String agent, LocalDateTime soldAt) {
        if (v == null) return;
        SaleTransaction s = new SaleTransaction();
        s.setSoldAt(soldAt);
        s.setVariant(v);
        s.setSkuSnapshot(v.getSku());
        s.setProductLineSnapshot(v.getProductLine());
        s.setVariantLabelSnapshot(v.getVariantLabel());
        s.setCategorySnapshot(v.getCategory());
        s.setQuantity(qty);
        s.setUnitBuySnapshot(v.getBuyPrice());
        s.setUnitSellPrice(v.getSellPrice());
        s.setCustomerName(cust);
        s.setCustomerPhone(phone);
        s.setCustomerArea(area);
        s.setPaymentMethod(pm);
        s.setPaymentRef(ref);
        s.setPaymentScreenshotPath(screenshot);
        s.setCreditCleared(cleared);
        s.setAgentName(agent);
        saleRepo.save(s);

        movementRepo.save(new StockMovement(
            soldAt, "OUT", v.getSku(),
            v.getProductLine() + " — " + v.getVariantLabel(),
            qty, v.getWarehouse(),
            "Agent Sale (" + pm + ") • " + cust + " (" + agent + ")"
        ));
    }
}
