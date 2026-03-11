package com.banka1.userService.repository;

import com.banka1.userService.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TokenRepository extends JpaRepository<RefreshToken,Long> {
    Optional<RefreshToken> findByValue (String refreshToken);
}
