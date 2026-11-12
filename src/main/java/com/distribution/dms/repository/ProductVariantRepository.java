package com.distribution.dms.repository;

import com.distribution.dms.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findBySku(String sku);
}
