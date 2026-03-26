package com.banka1.transaction_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class NewPaymentDto {
    @NotBlank(message = "Unesi racun posiljaoca")
    @Pattern(regexp = "^\\d{18}$", message = "Broj racuna mora imati 18 cifara")
    private String fromAccountNumber;
    @NotBlank(message = "Unesi racun primaoca")
    @Pattern(regexp = "^\\d{18}$", message = "Broj racuna mora imati 18 cifara")
    private String toAccountNumber;
    @NotNull(message = "Unesi iznos")
    private BigDecimal amount;
    @NotBlank(message = "Unesi naziv primaoca")
    private String recipientName;
    @NotNull(message = "Unesi sifru placanja")
    @Pattern(regexp = "^2.*", message = "Sifra mora poceti sa 2")
    @Pattern(regexp = "^\\d{3}$", message = "Sifra mora imati tacno 3 cifre")
    private String paymentCode;
    //todo moze biti broj i slova, ako je samo broj onda postaje Integer ili Long
    private String referenceNumber;
    @NotBlank(message = "Unesi svrhu placanja")
    private String paymentPurpose;
    @NotBlank(message = "Unesi kod za verifikaciju")
    private String verificationCode;
    @NotNull(message = "Unesi verification session ID")
    private Long verificationSessionId;

}
