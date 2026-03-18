package com.banka1.account_service.repository;

import com.banka1.account_service.domain.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account,Long> {
    boolean existsByBrojRacuna(String brojRacuna);
    @Query("""
    SELECT a
    FROM Account a
    WHERE (:brojRacuna IS NULL OR LOWER(a.brojRacuna) LIKE LOWER(CONCAT('%', :brojRacuna, '%')))
      AND a.vlasnik IN :ownerIds
""")
    Page<Account> searchAccounts(
            @Param("brojRacuna") String brojRacuna,
            @Param("ownerIds") List<Long> ownerIds,
            Pageable pageable
    );
}
