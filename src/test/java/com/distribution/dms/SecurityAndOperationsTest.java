package com.distribution.dms;

import com.distribution.dms.model.AppUser;
import com.distribution.dms.model.ProductVariant;
import com.distribution.dms.model.SaleTransaction;
import com.distribution.dms.repository.AppUserRepository;
import com.distribution.dms.repository.ProductVariantRepository;
import com.distribution.dms.repository.SaleTransactionRepository;
import com.distribution.dms.service.AdminPasswordResetService;
import com.distribution.dms.service.PasswordMigrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
public class SecurityAndOperationsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository userRepo;

    @Autowired
    private ProductVariantRepository variantRepo;

    @Autowired
    private SaleTransactionRepository saleRepo;

    @Autowired
    private BCryptPasswordEncoder encoder;

    @Autowired
    private PasswordMigrationService migrationService;

    @Autowired
    private AdminPasswordResetService resetService;

    @Autowired
    private ObjectMapper om;

    @BeforeEach
    void setupTestData() {
        saleRepo.deleteAll();
        variantRepo.deleteAll();
        userRepo.deleteAll();

        // Seed initial admin and agent
        userRepo.save(new AppUser("admin", encoder.encode("adminSecret123"), "System Admin", "ADMIN", null));
        userRepo.save(new AppUser("agent1", encoder.encode("agentSecret123"), "Agent Dawit", "AGENT", "Route A"));

        // Seed initial product
        ProductVariant variant = new ProductVariant(
                "SKU-TEST-01", "Baby Diapers", "Size 1", "Diapers",
                "Main Warehouse", "Factory A",
                new BigDecimal("300.00"), new BigDecimal("400.00"), 50, 10
        );
        variantRepo.save(variant);
    }

    @Test
    @DisplayName("1. Login with valid credentials succeeds and creates session")
    void testValidLogin() throws Exception {
        Map<String, String> payload = Map.of("username", "admin", "password", "adminSecret123");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("2. Login with incorrect password is rejected")
    void testInvalidPasswordRejected() throws Exception {
        Map<String, String> payload = Map.of("username", "admin", "password", "wrongPassword");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    @DisplayName("3. BCrypt migration hashes legacy plaintext passwords properly")
    void testPasswordMigration() throws Exception {
        AppUser legacyUser = new AppUser("legacyAgent", "plainPassword123", "Legacy User", "AGENT", "Route B");
        userRepo.save(legacyUser);

        // Run migration
        migrationService.migrateAllPasswords(userRepo, encoder);

        AppUser updated = userRepo.findByUsername("legacyAgent").orElseThrow();
        assertTrue(updated.getPasswordHash().startsWith("$2a$") || updated.getPasswordHash().startsWith("$2b$"));
        assertTrue(encoder.matches("plainPassword123", updated.getPasswordHash()));

        // Login with migrated password
        Map<String, String> loginReq = Map.of("username", "legacyAgent", "password", "plainPassword123");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("legacyAgent"));
    }

    @Test
    @DisplayName("4. Creating a new agent securely hashes password with BCrypt")
    void testCreateNewAgent() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        Map<String, String> newAgentReq = Map.of(
                "username", "newbie",
                "password", "securePass2026",
                "fullName", "Newbie Agent",
                "routeName", "Route East"
        );

        mvc.perform(post("/api/auth/create-agent")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(newAgentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newbie"));

        AppUser stored = userRepo.findByUsername("newbie").orElseThrow();
        assertNotEquals("securePass2026", stored.getPasswordHash());
        assertTrue(encoder.matches("securePass2026", stored.getPasswordHash()));
    }

    @Test
    @DisplayName("5. Password verification allows newly created agent to authenticate")
    void testPasswordVerificationForNewAgent() throws Exception {
        // Pre-create an agent with BCrypt hashed password
        userRepo.save(new AppUser("agentCreated", encoder.encode("agentPass2026"), "Created Agent", "AGENT", "Route C"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "agentCreated", "password", "agentPass2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("agentCreated"))
                .andExpect(jsonPath("$.role").value("AGENT"));
    }

    @Test
    @DisplayName("6. Admin-only endpoints reject agent or unauthenticated users")
    void testAdminOnlyEndpoints() throws Exception {
        // Unauthenticated request
        mvc.perform(get("/api/auth/agents"))
                .andExpect(status().isForbidden());

        // Agent session request
        MockHttpSession agentSession = new MockHttpSession();
        agentSession.setAttribute("role", "AGENT");
        agentSession.setAttribute("username", "agent1");

        mvc.perform(get("/api/auth/agents").session(agentSession))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/analytics/monthly-profit").session(agentSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7. Agent view of variants strictly hides unit profit and factory buy price")
    void testAgentVariantSanitization() throws Exception {
        MockHttpSession agentSession = new MockHttpSession();
        agentSession.setAttribute("role", "AGENT");
        agentSession.setAttribute("fullName", "Agent Dawit");

        mvc.perform(get("/api/variants").session(agentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-TEST-01"))
                .andExpect(jsonPath("$[0].sellPrice").value(400.00))
                .andExpect(jsonPath("$[0].buyPrice").doesNotExist())
                .andExpect(jsonPath("$[0].unitProfit").doesNotExist());
    }

    @Test
    @DisplayName("8. Unauthenticated requests to /api/auth/me return 401 Unauthorized")
    void testSessionExpirationOrMissing() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("9. Agent sale deduction and dashboard operations succeed")
    void testRecordSaleAndStockDeduction() throws Exception {
        MockHttpSession agentSession = new MockHttpSession();
        agentSession.setAttribute("role", "AGENT");
        agentSession.setAttribute("fullName", "Agent Dawit");

        Map<String, Object> saleReq = Map.of(
                "sku", "SKU-TEST-01",
                "quantity", 5,
                "customerName", "Retail Supermarket",
                "customerPhone", "0911000000",
                "customerArea", "Bole",
                "paymentMethod", "CASH",
                "paymentRef", "Cash Sale"
        );

        mvc.perform(post("/api/sales")
                        .session(agentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(saleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.customerName").value("Retail Supermarket"))
                .andExpect(jsonPath("$.unitBuySnapshot").doesNotExist());

        // Verify stock was reduced from 50 to 45
        ProductVariant updated = variantRepo.findBySku("SKU-TEST-01").orElseThrow();
        assertEquals(45, updated.getOnHand());
    }

    @Test
    @DisplayName("10. Admin password reset endpoint security and execution")
    void testAdminPasswordResetSecurity() throws Exception {
        // 1. Unauthenticated reset fails
        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "agent1", "newPassword", "brandNewPass123"))))
                .andExpect(status().isForbidden());

        // 2. Agent cannot reset password
        MockHttpSession agentSession = new MockHttpSession();
        agentSession.setAttribute("role", "AGENT");
        agentSession.setAttribute("username", "agent1");

        mvc.perform(post("/api/auth/reset-password")
                        .session(agentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "admin", "newPassword", "brandNewPass123"))))
                .andExpect(status().isForbidden());

        // 3. Admin can reset password
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        mvc.perform(post("/api/auth/reset-password")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "agent1", "newPassword", "brandNewPass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successful for user: agent1"));

        // Verify agent can now login with new password
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "agent1", "password", "brandNewPass123"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("11. Forced password change workflow: temporary reset, old password rejection, forced update")
    void testForcedPasswordChangeWorkflow() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        // 1. Admin resets agent1 with temporary password and forceChangeOnNextLogin=true
        mvc.perform(post("/api/auth/reset-password")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "username", "agent1",
                                "newPassword", "TempAgentPass1",
                                "forceChangeOnNextLogin", "true"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forceChangeOnNextLogin").value(true));

        // 2. Old password is now rejected
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "agent1", "password", "agentSecret123"))))
                .andExpect(status().isUnauthorized());

        // 3. Login with temporary password succeeds and flags mustChangePassword=true
        String loginResp = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "agent1", "password", "TempAgentPass1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();

        // 4. Agent attempts self-service change with weak password (< 8 chars) -> rejected
        MockHttpSession agentSession = new MockHttpSession();
        agentSession.setAttribute("role", "AGENT");
        agentSession.setAttribute("username", "agent1");

        mvc.perform(post("/api/auth/change-password")
                        .session(agentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "currentPassword", "TempAgentPass1",
                                "newPassword", "weak1"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password must be at least 8 characters"));

        // 5. Agent attempts self-service change with no digits -> rejected
        mvc.perform(post("/api/auth/change-password")
                        .session(agentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "currentPassword", "TempAgentPass1",
                                "newPassword", "onlylettershere"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password must contain at least one letter and one number"));

        // 6. Agent performs self-service change with valid permanent password
        mvc.perform(post("/api/auth/change-password")
                        .session(agentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "currentPassword", "TempAgentPass1",
                                "newPassword", "PermAgentPass2026"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        // 7. Temporary password is now rejected
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "agent1", "password", "TempAgentPass1"))))
                .andExpect(status().isUnauthorized());

        // 8. New permanent password succeeds with mustChangePassword=false
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "agent1", "password", "PermAgentPass2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    @DisplayName("12. Admin self-service password change: validation, old password rejection, update")
    void testAdminSelfServicePasswordChange() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        // 1. Wrong current password -> rejected
        mvc.perform(post("/api/auth/change-password")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "currentPassword", "wrongCurrentPass",
                                "newPassword", "AdminSecure2026"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Current password is incorrect"));

        // 2. Identical new password -> rejected
        mvc.perform(post("/api/auth/change-password")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "currentPassword", "adminSecret123",
                                "newPassword", "adminSecret123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("New password cannot be identical to the current password"));

        // 3. Valid self-service change -> succeeds
        mvc.perform(post("/api/auth/change-password")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "currentPassword", "adminSecret123",
                                "newPassword", "AdminSecure2026"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        // 4. Old password rejected
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "admin", "password", "adminSecret123"))))
                .andExpect(status().isUnauthorized());

        // 5. New password succeeds
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "admin", "password", "AdminSecure2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("13. Agent deactivation rejects login, reactivation restores login")
    void testAgentDeactivationAndReactivation() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        AppUser agent = userRepo.findByUsername("agent1").orElseThrow();

        // 1. Deactivate agent
        mvc.perform(post("/api/auth/agents/" + agent.getId() + "/deactivate")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // 2. Deactivated agent login is rejected with HTTP 403
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "agent1", "password", "agentSecret123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Account has been deactivated. Please contact your system administrator."));

        // 3. Reactivate agent
        mvc.perform(post("/api/auth/agents/" + agent.getId() + "/reactivate")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // 4. Reactivated agent login succeeds
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", "agent1", "password", "agentSecret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("agent1"));
    }

    @Test
    @DisplayName("14. Safe agent deletion: blocked when agent has historical sales, allowed when zero sales")
    void testAgentDeletionSafety() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        AppUser agent = userRepo.findByUsername("agent1").orElseThrow();

        // Record a sale by this agent
        SaleTransaction sale = new SaleTransaction();
        sale.setAgentName(agent.getFullName());
        sale.setQuantity(2);
        sale.setUnitSellPrice(new BigDecimal("400.00"));
        sale.setUnitBuySnapshot(new BigDecimal("300.00"));
        sale.setPaymentMethod("CASH");
        sale.setCreditCleared(true);
        saleRepo.save(sale);

        // Attempt deletion -> Conflict (HTTP 409)
        mvc.perform(delete("/api/auth/agents/" + agent.getId())
                        .session(adminSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("error").value(org.hamcrest.Matchers.containsString("historical sales record")));

        // Create an agent with 0 sales
        AppUser freshAgent = userRepo.save(new AppUser("cleanAgent", encoder.encode("cleanPass123"), "Clean Agent", "AGENT", "Route X"));

        // Deleting agent with 0 sales succeeds
        mvc.perform(delete("/api/auth/agents/" + freshAgent.getId())
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Agent account deleted successfully"));

        assertTrue(userRepo.findByUsername("cleanAgent").isEmpty());
    }

    @Test
    @DisplayName("15. Admin can update agent details (name, route)")
    void testUpdateAgentDetails() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        AppUser agent = userRepo.findByUsername("agent1").orElseThrow();

        Map<String, String> updateReq = Map.of(
                "fullName", "Dawit Mekonnen Updated",
                "routeName", "Bole & Gerji Express"
        );

        mvc.perform(put("/api/auth/agents/" + agent.getId())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Dawit Mekonnen Updated"))
                .andExpect(jsonPath("$.routeName").value("Bole & Gerji Express"));

        AppUser stored = userRepo.findById(agent.getId()).orElseThrow();
        assertEquals("Dawit Mekonnen Updated", stored.getFullName());
        assertEquals("Bole & Gerji Express", stored.getRouteName());
    }

    @Test
    @DisplayName("16. Payment screenshot upload validation, traversal protection, and retrieval")
    void testScreenshotUploadAndSecurity() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        // 1. Valid PNG upload
        org.springframework.mock.web.MockMultipartFile validFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "receipt.png", "image/png", "sample-image-bytes".getBytes()
        );

        String uploadResp = mvc.perform(multipart("/api/sales/upload-screenshot")
                        .file(validFile)
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").exists())
                .andExpect(jsonPath("$.url").exists())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> respMap = om.readValue(uploadResp, Map.class);
        String filename = (String) respMap.get("filename");

        // 2. Retrieve the uploaded screenshot
        mvc.perform(get("/api/screenshots/" + filename).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));

        // 3. Reject invalid file extension (.exe)
        org.springframework.mock.web.MockMultipartFile invalidFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "malicious.exe", "application/octet-stream", "bad-bytes".getBytes()
        );

        mvc.perform(multipart("/api/sales/upload-screenshot")
                        .file(invalidFile)
                        .session(adminSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Unsupported file type")));

        // 4. Directory traversal attempt is rejected
        mvc.perform(get("/api/screenshots/..%2f..%2fetc%2fpasswd").session(adminSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("17. Actuator health endpoint returns UP")
    void testActuatorHealth() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("18. Data endpoints are blocked when password change is required")
    void testBlockedEndpointsWhenPasswordResetRequired() throws Exception {
        // Create session with mustChangePassword=true
        MockHttpSession restrictedSession = new MockHttpSession();
        restrictedSession.setAttribute("role", "AGENT");
        restrictedSession.setAttribute("username", "agent1");
        restrictedSession.setAttribute("mustChangePassword", true);

        // Accessing variants is blocked with 403
        mvc.perform(get("/api/variants").session(restrictedSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // Accessing sales is blocked with 403
        mvc.perform(get("/api/sales").session(restrictedSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // Password change is permitted
        mvc.perform(post("/api/auth/change-password")
                        .session(restrictedSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "currentPassword", "agentSecret123",
                                "newPassword", "FreshAgentPass2026"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        // Now variants can be accessed
        mvc.perform(get("/api/variants").session(restrictedSession))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("19. Optimistic locking @Version entity protection tracks entity version")
    void testOptimisticLockingVersionTracking() {
        ProductVariant variant = variantRepo.findBySku("SKU-TEST-01").orElseThrow();
        Long initialVersion = variant.getVersion();
        assertNotNull(initialVersion);
        assertEquals(0L, initialVersion);

        // Update variant
        variant.setOnHand(45);
        ProductVariant updated = variantRepo.saveAndFlush(variant);

        // Version was incremented by Hibernate optimistic locking
        assertTrue(updated.getVersion() > initialVersion);
    }

    @Test
    @DisplayName("20. Debtor progressive installment payments (pay debt little by little)")
    void testPartialDebtInstallmentPayments() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        // 1. Record a credit sale of 10 units @ 400.00 = ETB 4,000.00
        Map<String, Object> saleReq = Map.of(
                "sku", "SKU-TEST-01",
                "quantity", 10,
                "customerName", "Mercato Retail Shop",
                "customerPhone", "0911223344",
                "customerArea", "Mercato",
                "paymentMethod", "CREDIT",
                "paymentRef", "15-day credit"
        );

        String saleRespStr = mvc.perform(post("/api/sales")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(saleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditCleared").value(false))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> createdSale = om.readValue(saleRespStr, Map.class);
        Long saleId = ((Number) createdSale.get("id")).longValue();

        // 2. Pay first installment: ETB 1,500
        Map<String, Object> payment1 = Map.of(
                "amount", 1500.00,
                "paymentMethod", "CASH",
                "note", "First installment cash receipt #001"
        );

        mvc.perform(post("/api/sales/" + saleId + "/pay-credit")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payment1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCleared").value(false))
                .andExpect(jsonPath("$.amountPaid").value(1500.00))
                .andExpect(jsonPath("$.remainingBalance").value(2500.00));

        // 3. Attempt to overpay: ETB 3,000 when remaining is 2,500 -> rejected with 400
        Map<String, Object> overpayment = Map.of(
                "amount", 3000.00,
                "paymentMethod", "MOBILE_BANKING",
                "note", "Attempted overpayment"
        );

        mvc.perform(post("/api/sales/" + saleId + "/pay-credit")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(overpayment)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("exceeds remaining balance")));

        // 4. Pay second installment: ETB 1,000 via Mobile Banking
        Map<String, Object> payment2 = Map.of(
                "amount", 1000.00,
                "paymentMethod", "MOBILE_BANKING",
                "note", "Telebirr transfer TB-994"
        );

        mvc.perform(post("/api/sales/" + saleId + "/pay-credit")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payment2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCleared").value(false))
                .andExpect(jsonPath("$.amountPaid").value(2500.00))
                .andExpect(jsonPath("$.remainingBalance").value(1500.00));

        // 5. Pay final installment: ETB 1,500 -> debt becomes fully cleared!
        Map<String, Object> payment3 = Map.of(
                "amount", 1500.00,
                "paymentMethod", "CASH",
                "note", "Final balance settlement"
        );

        mvc.perform(post("/api/sales/" + saleId + "/pay-credit")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payment3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCleared").value(true))
                .andExpect(jsonPath("$.amountPaid").value(4000.00))
                .andExpect(jsonPath("$.remainingBalance").value(0.00));

        // 6. Attempting to pay an already settled debt is rejected
        mvc.perform(post("/api/sales/" + saleId + "/pay-credit")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("amount", 500.00))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("already fully settled")));
    }

    @Test
    @DisplayName("21. Controlled Stock-In: Agent without permission gets 403; Admin can enable permission; Agent can then stock-in")
    void testControlledStockInPermission() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        // 1. Create a new agent with canAddStock = false (default)
        Map<String, String> newAgentReq = Map.of(
                "username", "stockAgent1",
                "password", "pass12345",
                "fullName", "Warehouse Trainee",
                "routeName", "Depot 1",
                "canAddStock", "false"
        );
        String createResp = mvc.perform(post("/api/auth/create-agent")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(newAgentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.canAddStock").value(false))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> respMap = om.readValue(createResp, Map.class);
        Number agentId = (Number) respMap.get("id");

        // 2. Trainee logs in -> session has canAddStock = false
        MockHttpSession traineeSession = new MockHttpSession();
        traineeSession.setAttribute("role", "AGENT");
        traineeSession.setAttribute("username", "stockAgent1");
        traineeSession.setAttribute("fullName", "Warehouse Trainee");
        traineeSession.setAttribute("canAddStock", false);

        // 3. Attempt stock-in -> rejected with HTTP 403 Forbidden
        mvc.perform(post("/api/variants/SKU-TEST-01/stock-in")
                        .session(traineeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("quantity", 50, "note", "Unauthorized attempt"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Stock-in permission required")));

        // 4. Admin updates agent to enable stock-in permission
        mvc.perform(put("/api/auth/agents/" + agentId)
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("canAddStock", "true"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canAddStock").value(true));

        // 5. Authorized agent session stocks in -> succeeds with HTTP 200
        MockHttpSession authorizedSession = new MockHttpSession();
        authorizedSession.setAttribute("role", "AGENT");
        authorizedSession.setAttribute("username", "stockAgent1");
        authorizedSession.setAttribute("fullName", "Warehouse Trainee");
        authorizedSession.setAttribute("canAddStock", true);

        mvc.perform(post("/api/variants/SKU-TEST-01/stock-in")
                        .session(authorizedSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("quantity", 25, "note", "Authorized batch intake"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-TEST-01"));
    }

    @Test
    @DisplayName("22. Admin Notifications: Triggered on stock-in and agent sale")
    void testAdminNotifications() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        // Verify notification list endpoint
        mvc.perform(get("/api/notifications")
                        .session(adminSession))
                .andExpect(status().isOk());

        // Verify unread count endpoint
        mvc.perform(get("/api/notifications/unread-count")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").isNumber());

        // Mark all read
        mvc.perform(post("/api/notifications/mark-all-read")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All notifications marked as read"));
    }

    @Test
    @DisplayName("23. In-App Messaging: Admin sends message to agent, agent reads it and replies")
    void testInAppMessaging() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");
        adminSession.setAttribute("fullName", "System Administrator");

        MockHttpSession agentSession = new MockHttpSession();
        agentSession.setAttribute("role", "AGENT");
        agentSession.setAttribute("username", "agent1");
        agentSession.setAttribute("fullName", "Agent One");

        // 1. Admin sends message to agent1
        mvc.perform(post("/api/messages/send")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "recipientUsername", "agent1",
                                "content", "Please prioritize Bole route deliveries today."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Please prioritize Bole route deliveries today."))
                .andExpect(jsonPath("$.read").value(false));

        // 2. Agent checks unread count -> at least 1
        mvc.perform(get("/api/messages/unread-count")
                        .session(agentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 3. Agent reads conversation thread -> marks message read
        mvc.perform(get("/api/messages/thread?withUser=admin")
                        .session(agentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));

        // 4. Agent replies to admin
        mvc.perform(post("/api/messages/send")
                        .session(agentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "recipientUsername", "admin",
                                "content", "Understood HQ, on my way to Bole now."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Understood HQ, on my way to Bole now."));

        // 5. Agent cannot message another agent (security boundary)
        mvc.perform(post("/api/messages/send")
                        .session(agentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "recipientUsername", "cleanAgent",
                                "content", "Hello peer agent"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("24. Finance & Daily Collections: Cash/Mobile collections aggregated; Credit sale not counted as cash")
    void testFinanceDailyCollections() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        mvc.perform(get("/api/finance/daily-collections")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCash").isNumber())
                .andExpect(jsonPath("$.totalMobileBanking").isNumber())
                .andExpect(jsonPath("$.totalDebtRepayments").isNumber())
                .andExpect(jsonPath("$.totalCollected").isNumber())
                .andExpect(jsonPath("$.transactionCount").isNumber())
                .andExpect(jsonPath("$.transactions").isArray());
    }

    @Test
    @DisplayName("25. Stock Movements Report and Daily Movement Chart Data")
    void testStockMovementsReporting() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "ADMIN");
        adminSession.setAttribute("username", "admin");

        // 1. Stock movements report endpoint
        mvc.perform(get("/api/stock-movements?range=month")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived").isNumber())
                .andExpect(jsonPath("$.totalDispatched").isNumber())
                .andExpect(jsonPath("$.currentOnHand").isNumber())
                .andExpect(jsonPath("$.movements").isArray());

        // 2. Comparative daily stock in vs stock out chart endpoint
        mvc.perform(get("/api/analytics/stock-movement-daily?days=7")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(7)))
                .andExpect(jsonPath("$[0].date").isNotEmpty())
                .andExpect(jsonPath("$[0].stockIn").isNumber())
                .andExpect(jsonPath("$[0].stockOut").isNumber());
    }
}

