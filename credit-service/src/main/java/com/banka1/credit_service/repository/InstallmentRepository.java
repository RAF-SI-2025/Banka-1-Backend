package com.banka1.credit_service.repository;

import com.banka1.credit_service.domain.Installment;
import com.banka1.credit_service.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface InstallmentRepository extends JpaRepository<Installment,Long> {
     List<Installment> findInstallmentByExpectedDueDateLessThanEqualAndPaymentStatusNot(LocalDate expectedDueDate, PaymentStatus paymentStatus);
}
