package com.banka1.account_service.repository;

import com.banka1.account_service.domain.Account;
import com.banka1.account_service.domain.enums.CardStatus;
import com.banka1.account_service.domain.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account,Long> {
    boolean existsByBrojRacuna(String brojRacuna);

    Page<Account> findByVlasnikAndStatus(Long id, Status status, Pageable pageable);

    Optional<Account> findByBrojRacuna(String brojRacuna);



    @Query("""
    SELECT a FROM Account a
    WHERE (:brojRacuna IS NULL OR LOWER(a.brojRacuna) LIKE LOWER(CONCAT('%', :brojRacuna, '%')))
    AND (:ime IS NULL OR LOWER(a.imeVlasnikaRacuna) LIKE LOWER(CONCAT('%', :ime, '%')))
    AND (:prezime IS NULL OR LOWER(a.prezimeVlasnikaRacuna) LIKE LOWER(CONCAT('%', :prezime, '%')))
    ORDER BY a.prezimeVlasnikaRacuna ASC, a.imeVlasnikaRacuna ASC
""")
    Page<Account> searchAccounts(
            @Param("brojRacuna") String brojRacuna,
            @Param("ime") String ime,
            @Param("prezime") String prezime,
            Pageable pageable
    );
}
