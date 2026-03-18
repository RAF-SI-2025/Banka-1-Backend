package com.banka1.account_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class NewPaymentDto {
    @NotBlank(message = "Unesi naziv primaoca")
    private String nazivPrimaoca;
    @NotBlank(message = "Unesi racun primaoca")
    @Pattern(regexp = "\\d{18}", message = "Broj racuna mora imati 18 cifara")
    private String racunPrimaoca;
    @NotNull(message = "Unesi iznos")
    private BigDecimal iznos;
    //todo moze biti broj i slova, ako je samo broj onda postaje Integer ili Long
    private String pozivNaBroj;
    @NotNull(message = "Unesi sifru placanja")
    @Pattern(regexp = "\\d{3}", message = "Sifra mora imati 3 cifre")
    private String sifraPlacanja;
    @NotBlank(message = "Unesi svrhu placanja")
    private String svrhaPlacanja;
    @NotBlank(message = "Unesi racun primaoca")
    @Pattern(regexp = "\\d{18}", message = "Broj racuna mora imati 18 cifara")
    private String racunPratioca;


}
