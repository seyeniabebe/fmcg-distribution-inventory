package com.distribution.dms.repository;

import com.distribution.dms.model.SaleTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SaleTransactionRepository extends JpaRepository<SaleTransaction, Long> {
    List<SaleTransaction> findByPaymentMethodAndCreditClearedFalse(String paymentMethod);

    /**
     * Returns rows: [year (int), month (int), totalProfit (BigDecimal), totalRevenue (BigDecimal), unitsSold (long)]
     * Grouped by calendar month across all time, ordered ascending.
     * Uses ANSI SQL EXTRACT(YEAR FROM ...) / EXTRACT(MONTH FROM ...) compatible with PostgreSQL and H2.
     */
    @Query(value = """
            SELECT EXTRACT(YEAR FROM sold_at)  AS yr,
                   EXTRACT(MONTH FROM sold_at) AS mo,
                   SUM((unit_sell_price - unit_buy_snapshot) * quantity) AS profit,
                   SUM(unit_sell_price * quantity) AS revenue,
                   SUM(quantity) AS units
            FROM   sale_transactions
            WHERE  sold_at IS NOT NULL
            GROUP  BY EXTRACT(YEAR FROM sold_at), EXTRACT(MONTH FROM sold_at)
            ORDER  BY yr, mo
            """, nativeQuery = true)
    List<Object[]> findMonthlyProfitNative();

    /** Agent-scoped sales by exact agentName */
    List<SaleTransaction> findByAgentName(String agentName);

    /** Count of sales recorded by an agent (used for deletion safety checks) */
    long countByAgentName(String agentName);

    /** Count of sales associated with a SKU snapshot or variant */
    @Query("SELECT COUNT(s) FROM SaleTransaction s WHERE s.skuSnapshot = :sku OR (s.variant IS NOT NULL AND s.variant.sku = :sku)")
    long countBySku(@Param("sku") String sku);
}
