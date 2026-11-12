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
}

