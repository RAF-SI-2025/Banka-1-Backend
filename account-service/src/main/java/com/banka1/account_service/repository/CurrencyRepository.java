package com.banka1.account_service.repository;

import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.domain.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency,Long> {
    Optional<Currency> findByOznaka(CurrencyCode oznaka);

    //lista posto se traze sve, ima ih malo, page je overkill ali ako se poveca broj redova moze
    List<Currency> findByStatus(Status status);

    Page<Currency> findByStatus(Status status, Pageable pageable);
}
