package com.distribution.dms.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash; // plain-text for dev; BCrypt in prod

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String role; // ADMIN or AGENT

    private String routeName; // e.g. "Bole & CMC Route"

    @Column(name = "password_reset_required", nullable = false)
    private boolean passwordResetRequired = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "can_add_stock", nullable = false)
    private boolean canAddStock = false;

    public AppUser() {}

    public AppUser(String username, String passwordHash, String fullName, String role, String routeName) {
        this(username, passwordHash, fullName, role, routeName, false, true, "ADMIN".equals(role));
    }

    public AppUser(String username, String passwordHash, String fullName, String role, String routeName, boolean passwordResetRequired) {
        this(username, passwordHash, fullName, role, routeName, passwordResetRequired, true, "ADMIN".equals(role));
    }

    public AppUser(String username, String passwordHash, String fullName, String role, String routeName, boolean passwordResetRequired, boolean active) {
        this(username, passwordHash, fullName, role, routeName, passwordResetRequired, active, "ADMIN".equals(role));
    }

    public AppUser(String username, String passwordHash, String fullName, String role, String routeName, boolean passwordResetRequired, boolean active, boolean canAddStock) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.routeName = routeName;
        this.passwordResetRequired = passwordResetRequired;
        this.active = active;
        this.canAddStock = "ADMIN".equals(role) || canAddStock;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }
    public boolean isPasswordResetRequired() { return passwordResetRequired; }
    public void setPasswordResetRequired(boolean passwordResetRequired) { this.passwordResetRequired = passwordResetRequired; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isCanAddStock() { return "ADMIN".equals(role) || canAddStock; }
    public void setCanAddStock(boolean canAddStock) { this.canAddStock = canAddStock; }
}
