# FMCG Distribution Inventory, Agent Sales & Debtor System

## Tech Stack
- **Backend**: Java 17+ with Spring Boot 3, Spring Data JPA, and H2 / PostgreSQL
- **Frontend**: Vanilla HTML5, Tailwind CSS, Chart.js, and ES6 JavaScript (`src/main/resources/static/index.html`)

## Core Domain Rules
1. **Product Catalog & Variants**:
   - Products belong to categories (`Diapers`, `Sanitary Pads`, `Wet Wipes`, `Tissue Paper`).
   - Diapers have multiple size variants (`Size 1 Newborn` through `Size 5 Junior`), each with its own SKU, Factory Buy Price, Wholesale Sell Price, and Warehouse Stock.
   - Sanitary Pads are registered as a single-variant product (`PAD-001`) alongside Wet Wipes and Tissue Paper.
2. **Warehouse Stock In / Out**:
   - Factory deliveries (`IN`) increment `onHand` and write an audit row to `stock_movements`.
   - Every agent sale (`OUT`) atomically validates and deducts `quantity` from `onHand`.
3. **Separate Admin & Field Agent Views**:
   - **Admin Dashboard**: Displays factory buy prices, wholesale sell prices, unit profit (`sellPrice - buyPrice`), realized net profit from sales, and warehouse valuation.
   - **Field Agent Portal**: Hides buy prices and profit margins; allows agents to log daily shop/supermarket sales paid via `CASH`, `MOBILE_BANKING`, or `CREDIT`.
4. **Customer / Debtor Credit Tracking**:
   - Sales paid via `CREDIT` remain `creditCleared = false` until settled in the **Debtors & Credit** tab.
