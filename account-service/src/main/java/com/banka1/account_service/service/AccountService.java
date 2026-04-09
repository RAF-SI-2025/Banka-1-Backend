package com.banka1.account_service.service;

import com.banka1.account_service.dto.request.BankPaymentDto;
import com.banka1.account_service.dto.request.PaymentDto;

import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.dto.response.InfoResponseDto;
import com.banka1.account_service.dto.response.InternalAccountDetailsDto;
import com.banka1.account_service.dto.response.UpdatedBalanceResponseDto;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Servis za izvrsavanje internih transakcija i transfera izmedju racuna.
 * Pozivaju ga interni servisi putem {@code /internal/accounts} endpointa.
 */
public interface AccountService {

    /**
     * Izvrsava transakciju izmedju racuna razlicitih vlasnika.
     * Validira oba racuna i banka-racune za svaku valutu, zatim prenosi sredstva.
     *
     * @param paymentDto podaci o placanju (brojevi racuna, iznosi, provizija, ID klijenta)
     * @return azurirana stanja oba racuna nakon transakcije
     */
    UpdatedBalanceResponseDto transaction(PaymentDto paymentDto);


    void transactionFromBank(BankPaymentDto paymentDto);

    /**
     * Izvrsava transfer izmedju dva racuna istog vlasnika.
     * Razlikuje se od {@link #transaction} po tome sto proverava da oba racuna
     * pripadaju istom vlasniku.
     *
     * @param paymentDto podaci o transferu (brojevi racuna, iznosi, provizija, ID klijenta)
     * @return azurirana stanja oba racuna nakon transfera
     */
    UpdatedBalanceResponseDto transfer(PaymentDto paymentDto);

    /**
     * Vraca informacije o valutama i vlasnicima dva racuna.
     * Koristi se od strane transaction-service-a za proveru pre izvrsavanja transakcije.
     *
     * @param jwt JWT token pozivaoca
     * @param fromAccountNumber broj racuna posiljaoca
     * @param toAccountNumber broj racuna primaoca
     * @return DTO sa valutama i ID-evima vlasnika oba racuna
     */
    InfoResponseDto info(Jwt jwt, String fromAccountNumber, String toAccountNumber);

    /**
     * Vraca detalje racuna po broju racuna.
     * Koristi se od strane transfer-service-a za proveru vlasnika i valute pre izvrsavanja transfera.
     *
     * @param accountNumber broj racuna
     * @return DTO sa detaljima racuna
     */
    InternalAccountDetailsDto getAccountDetails(String accountNumber);

    /**
     * Vraca detalje racuna po internom ID-u.
     *
     * @param accountId identifikator racuna
     * @return DTO sa detaljima racuna
     */
    InternalAccountDetailsDto getAccountDetails(Long accountId);

    /**
     * Vraca interni bankovni/drzavni racun za zadatu valutu.
     *
     * @param currencyCode kod valute
     * @return DTO sa detaljima internog racuna
     */
    InternalAccountDetailsDto getBankAccountDetails(CurrencyCode currencyCode);
}
