package com.banka1.account_service.dto.response;

import com.banka1.account_service.domain.Account;

import java.math.BigDecimal;

/**
 * DTO za interne pozive izmedju servisa (npr. transfer-service).
 * Sadrzi osnovne informacije o racunu sa engleskim imenima polja
 * kako bi bili kompatibilni sa ocekivanim formatom koji koriste drugi servisi.
 */
public record InternalAccountDetailsDto(
        String accountNumber,
        Long ownerId,
        String currency,
        BigDecimal availableBalance,
        String status,
        String accountType,
        String email,
        String username
) {
    public static InternalAccountDetailsDto from(Account account) {
        String accountType = null;
        if (account instanceof com.banka1.account_service.domain.CheckingAccount ca) {
            accountType = ca.getAccountConcrete().getAccountOwnershipType().name();
        } else if (account instanceof com.banka1.account_service.domain.FxAccount fa) {
            accountType = fa.getAccountOwnershipType().name();
        }
        return new InternalAccountDetailsDto(
                account.getBrojRacuna(),
                account.getVlasnik(),
                account.getCurrency() != null ? account.getCurrency().getOznaka().name() : null,
                account.getRaspolozivoStanje(),
                account.getStatus() != null ? account.getStatus().name() : null,
                accountType,
                account.getEmail(),
                account.getUsername()
        );
    }
}
