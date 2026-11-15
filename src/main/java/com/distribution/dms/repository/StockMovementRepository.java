package com.distribution.dms.repository;

import com.distribution.dms.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    void deleteBySku(String sku);
    List<StockMovement> findBySkuOrderByTimestampDesc(String sku);
}
