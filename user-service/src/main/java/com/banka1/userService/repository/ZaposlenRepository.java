package com.banka1.userService.repository;

import com.banka1.userService.domain.Zaposlen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZaposlenRepository extends JpaRepository<Zaposlen, Long> {

    Optional<Zaposlen> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    Optional<Zaposlen> findByUsername(String username);

    @Query("SELECT z FROM Zaposlen z WHERE " +
            "LOWER(z.ime) LIKE LOWER(CONCAT('%', :ime, '%')) AND " +
            "LOWER(z.prezime) LIKE LOWER(CONCAT('%', :prezime, '%')) AND " +
            "LOWER(z.email) LIKE LOWER(CONCAT('%', :email, '%')) AND " +
            "LOWER(z.departman) LIKE LOWER(CONCAT('%', :departman, '%')) AND " +
            "LOWER(z.pozicija) LIKE LOWER(CONCAT('%', :pozicija, '%'))")
    Page<Zaposlen> searchEmployees(
            @Param("ime") String ime,
            @Param("prezime") String prezime,
            @Param("email") String email,
            @Param("departman") String departman,
            @Param("pozicija") String pozicija,
            Pageable pageable
    );

    @Query("SELECT z FROM Zaposlen z WHERE " +
            "LOWER(z.ime) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(z.prezime) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(z.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(z.departman) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(z.pozicija) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Zaposlen> globalSearchEmployees(@Param("query") String query, Pageable pageable);
}
