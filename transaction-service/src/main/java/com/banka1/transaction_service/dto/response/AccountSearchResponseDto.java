package com.banka1.transaction_service.dto.response;


import com.banka1.transaction_service.domain.enums.AccountOwnershipType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountSearchResponseDto {
    private String brojRacuna;
    private String ime;
    private String prezime;
    private AccountOwnershipType accountOwnershipType;
    private String tekuciIliDevizni;


}
