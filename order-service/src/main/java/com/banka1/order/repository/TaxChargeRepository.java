package com.banka1.order.repository;

import com.banka1.order.entity.TaxCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaxChargeRepository extends JpaRepository<TaxCharge, Long> {
    boolean existsBySellTransactionIdAndBuyTransactionId(Long sellTransactionId, Long buyTransactionId);
}
