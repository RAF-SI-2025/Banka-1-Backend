package com.banka1.credit_service.repository;

import com.banka1.credit_service.domain.Loan;
import com.banka1.credit_service.domain.enums.LoanType;
import com.banka1.credit_service.domain.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan,Long> {

    Page<Loan> findByClientIdOrderByAmountDesc(Long clientId, Pageable pageable);

    @Query("""
    SELECT l
    FROM Loan l
    WHERE (:loanType IS NULL OR l.loanType = :loanType)
      AND (:accountNumber IS NULL OR l.accountNumber = :accountNumber)
      AND (:status IS NULL OR l.status = :status)
    ORDER BY l.accountNumber ASC
""")
    Page<Loan> findAllWithFilters(
            @Param("loanType") LoanType loanType,
            @Param("accountNumber") String accountNumber,
            @Param("status") Status status,
            Pageable pageable
    );

}
