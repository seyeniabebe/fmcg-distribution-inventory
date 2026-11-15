# FMCG Distribution Management System

Production-ready, full-stack **Java Spring Boot 3 + PostgreSQL + Tailwind CSS / Vanilla JS** distribution management system designed for Ethiopian FMCG distributors (Factories → Central Warehouse → Field Agents → Retail Supermarkets / Mini Markets).

---

## 1. System Architecture & Features

- **Product & SKU Variant Catalog**:
  - Diaper variants (Size 1 Newborn through Size 5 Junior), single-variant Sanitary Pads, Wet Wipes, and Tissue Paper.
  - Multi-tier pricing: Factory Buy Price, Wholesale Sell Price, Unit Profit margin.
  - Optimistic locking via `@Version` to strictly prevent concurrent overselling across field agents.
- **Warehouse Stock Movement**:
  - Factory Stock-In receipts automatically increment warehouse inventory and append an immutable audit log to `stock_movements`.
  - Field agent sales deduct inventory within atomic transactions.
- **Role-Based Access Control (RBAC)**:
  - **Admin HQ Mode**: Full visibility into unit margins, warehouse buy valuation, realized net profit trends, debtor settlement, factory replenishment, and agent administration.
  - **Field Agent Sales Portal**: Buy prices and profit margins are strictly sanitized and never exposed over the wire; agents log shop deliveries paid via `CASH`, `MOBILE_BANKING`, or `CREDIT`.
- **Agent Management Module (Admin HQ)**:
  - Create new field representatives with route assignments.
  - Edit agent details and territory assignments.
  - Deactivate agents (immediately revokes login access; preserves all historical transactions and audit trail).
  - Reactivate archived agents.
  - Safe deletion: permanently blocks deletion if an agent has historical sales records.
  - Admin password reset workflow with forced password change on next login.
- **Security Hardening**:
  - Passwords hashed using Spring Security `BCryptPasswordEncoder` (cost factor 10).
  - Passwords and hashes are never printed in server logs or API responses.
  - Session cookie protections: `HttpOnly`, `SameSite=Lax`, and configurable `Secure` flag.
  - Session fixation protection: invalidates prior unauthenticated session upon login.
  - Force-password-change workflow: temporary passwords require replacement before accessing business APIs.
- **Payment Verification & Screenshot Storage**:
  - Field agents upload mobile banking receipt slips (Telebirr, CBE Birr).
  - Persistent, configurable upload directory (`UPLOAD_DIR`, default `/var/data/screenshots` in cloud, `uploads/screenshots` in local dev).
  - Extension whitelist (`.png`, `.jpg`, `.jpeg`, `.webp`), 10MB size limit, UUID file name sanitization, path traversal prevention.
- **Cloud Observability**:
  - Spring Boot Actuator health endpoint (`/actuator/health`).

---

## 2. Environment Variables

| Variable | Description | Default / Example |
| :--- | :--- | :--- |
| `PORT` | HTTP port for the web service (Render/Railway injects this) | `8080` |
| `DATABASE_URL` | Cloud PostgreSQL URI (`postgres://user:pass@host:port/db`) | Automatically converted to JDBC |
| `DB_URL` | Direct JDBC connection string | `jdbc:postgresql://localhost:5432/fmcg_dms` |
| `DB_USERNAME` | PostgreSQL database user | `fmcg_user` |
| `DB_PASSWORD` | PostgreSQL database password | *(secret)* |
| `UPLOAD_DIR` | Persistent directory for payment screenshots | `/var/data/screenshots` |
| `SESSION_COOKIE_SECURE` | Enforce HTTPS-only cookie transmission | `true` in production, `false` in local dev |
| `MIGRATE_PASSWORDS` | One-time legacy plaintext password migration flag | `false` |
| `ROTATE_COMPROMISED_PASSWORDS` | One-time credential rotation flag | `false` |

---

## 3. Local Development Setup

### Prerequisites
- Java 17+ (JDK 17 or JDK 21 recommended)
- PostgreSQL 13+ (or in-memory H2 for zero-config testing)
- Apache Maven 3.8+

### Setup Steps
1. Clone the repository:
   ```bash
   git clone <repo-url>
   cd inventory
   ```
2. Configure your environment:
   ```bash
   cp .env.example .env
   # Edit .env with your local PostgreSQL credentials
   ```
3. Run safe database schema updates:
   ```bash
   psql -h localhost -U fmcg_user -d fmcg_dms -f src/main/resources/schema-updates.sql
   ```
4. Run the test suite:
   ```bash
   mvn test
   ```
5. Start the application:
   ```bash
   source .env 2>/dev/null
   mvn spring-boot:run
   ```
6. Open `http://localhost:8080` in your browser.

---

## 4. Cloud Deployment: Render

This repository includes a native **Render Blueprint** (`render.yaml`).

1. Connect your repository to [Render](https://render.com).
2. Click **New +** → **Blueprint**.
3. Select this repository. Render reads `render.yaml` and provisions:
   - A managed **PostgreSQL database** (`fmcg_dms`).
   - A Docker web service configured with dynamic `${PORT}` and `/actuator/health`.
   - A **persistent disk** (`10 GB`) mounted at `/var/data/screenshots`.
4. Deploy!

---

## 5. Cloud Deployment: Railway

1. Install the Railway CLI or connect via the [Railway Dashboard](https://railway.app).
2. Create a new project and add:
   - A **PostgreSQL service** (Railway automatically sets `DATABASE_URL`).
   - A **GitHub Repo service** (pointing to this repo).
3. Under service settings:
   - Add a persistent volume mounted at `/var/data/screenshots`.
   - Set environment variables:
     - `UPLOAD_DIR=/var/data/screenshots`
     - `SESSION_COOKIE_SECURE=true`
4. Railway builds using the multi-stage `Dockerfile` and monitors `/actuator/health`.

---

## 6. Security Notice: Initial Credential Distribution

When initial credentials or temporary passwords are generated, they are written to a secured file:
`data/initial_credentials_distribution.txt` (POSIX permission `600`, excluded from Git).

Distribute temporary passwords to staff through secure internal channels. Upon their first login, users will be required to choose a new permanent password before accessing application data.
