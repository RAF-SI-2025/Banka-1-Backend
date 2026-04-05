package com.banka1.order.repository;

import com.banka1.order.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link Transaction} entities.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Returns all executed transaction portions for a given order.
     *
     * @param orderId the parent order's identifier
     * @return list of transactions in chronological insertion order
     */
    List<Transaction> findByOrderId(Long orderId);
}
