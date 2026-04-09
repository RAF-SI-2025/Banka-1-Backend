package com.banka1.transaction_service.repository;

import com.banka1.transaction_service.domain.Payment;
import com.banka1.transaction_service.domain.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repository interface for managing Payment entities.
 * Provides methods for querying and persisting payment data.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment,Long>, JpaSpecificationExecutor<Payment> {

    /**
     * Updates the status of "stuck" transactions that have remained in the IN_PROGRESS status
     * longer than the specified time threshold.
     * <p>
     * This method is used in cleanup tasks that detect and resolve
     * unsuccessfully completed transactions.
     *
     * @param oldStatus the old status to be changed (usually IN_PROGRESS)
     * @param newStatus the new status to set (usually DENIED)
     * @param threshold the time threshold - transactions created before this time
     * @return the number of updated rows
     */
    @Modifying
    @Query("""
    UPDATE Payment p
    SET p.status = :newStatus
    WHERE p.status = :oldStatus
    AND p.createdAt < :threshold
""")
    int markStuckPayments(
            TransactionStatus oldStatus,
            TransactionStatus newStatus,
            LocalDateTime threshold
    );

    /**
     * Retrieves all transactions associated with a specific account number.
     * <p>
     * Finds all transactions where the given account is either the sender or the receiver,
     * sorted by creation time (most recent first).
     *
     * @param accountNumber the account number
     * @param pageable pagination parameters
     * @return a paginated list of transactions for the given account
     */
    @Query("""
    SELECT p
    FROM Payment p
    WHERE p.fromAccountNumber = :accountNumber
       OR p.toAccountNumber = :accountNumber
    ORDER BY p.createdAt DESC
""")
    Page<Payment> findByAccountNumber(
            @Param("accountNumber") String accountNumber,
            Pageable pageable
    );
}
