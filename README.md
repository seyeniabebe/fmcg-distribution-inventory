# FMCG Distribution Management System (Google Antigravity Project)

This full-stack **Java Spring Boot 3 + HTML/CSS/JS** project is pre-configured for **Google Antigravity IDE** (`https://antigravity.google/`).

## Project Structure
- `GEMINI.md` & `.agents/skills/fmcg-distribution/SKILL.md`: Workspace context and agent skill rules for Google Antigravity.
- `pom.xml`: Maven configuration for Spring Boot 3, Spring Web, Spring Data JPA, H2 (zero-config local database), and PostgreSQL.
- `src/main/java/com/distribution/dms/`:
  - `DmsApplication.java`: Main application & catalog seeder (Diaper sizes 1-5, single-variant Sanitary Pads, Wet Wipes, Tissue Paper).
  - `model/ProductVariant.java`: Product & variant entity with automatic `getUnitProfit()` (`sellPrice - buyPrice`).
  - `model/SaleTransaction.java`: Agent sale entity with payment method (`CASH`, `MOBILE_BANKING`, `CREDIT`) and debtor status.
  - `model/StockMovement.java`: Warehouse Stock-In and Stock-Out audit log entity.
  - `repository/`: Spring Data JPA repositories.
  - `service/DistributionService.java`: `@Transactional` stock deduction, factory stock-in, and credit settlement logic.
  - `controller/DistributionController.java`: REST API endpoints (`/api/variants`, `/api/sales`, `/api/movements`).
- `src/main/resources/static/index.html`: The complete Admin HQ Dashboard + Field Agent Sales Portal + Debtor Credit Tracker UI.

## How to Run in Google Antigravity
1. Open this folder in **Google Antigravity IDE**.
2. Run in the terminal:
   ```bash
   mvn spring-boot:run
   ```
3. Open `http://localhost:8080` in your browser.
