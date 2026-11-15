package com.distribution.dms.repository;

import com.distribution.dms.model.DebtPaymentInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DebtPaymentInstallmentRepository extends JpaRepository<DebtPaymentInstallment, Long> {
    List<DebtPaymentInstallment> findBySaleIdOrderByPaidAtAsc(Long saleId);
    List<DebtPaymentInstallment> findAllByOrderByPaidAtDesc();
    List<DebtPaymentInstallment> findByPaidAtBetweenOrderByPaidAtDesc(LocalDateTime start, LocalDateTime end);
    List<DebtPaymentInstallment> findByAgentNameOrderByPaidAtDesc(String agentName);
}
