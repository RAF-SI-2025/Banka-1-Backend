package com.banka1.account_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO za zahtev izvršavanja finansijske transakcije ili transfera.
 * <p>
 * Koristi se za intra-bank transfere novca između računa, sa mogućnošću konverzije
 * između različitih valuta i nabijanjem komisije. Sadrži sve potrebne podatke za
 * validaciju i izvršavanje transakcije između dva računa.
 * <p>
 * Validacija:
 * <ul>
 *   <li>Oba broja računa moraju biti 19-cifreni</li>
 *   <li>Iznosi (fromAmount i toAmount) moraju biti pozitivni (> 0)</li>
 *   <li>Komisija mora biti >= 0</li>
 *   <li>Klijent ID mora biti popunjen</li>
 * </ul>
 * <p>
 * Primer korišćenja: Transfer od 1000.00 RSD sa 10.00 RSD komisije konvertuje se
 * na 950.95 EUR (ako se radi o межвалутном transferu).
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PaymentDto {
    /**
     * Broj računa sa kojeg se novac prenosi (19 cifara).
     */
    @NotBlank(message = "Unesi racun posiljaoca")
    @Pattern(regexp = "^\\d{19}$", message = "Broj racuna mora imati 19 cifara")
    private String fromAccountNumber;

    /**
     * Broj računa na koji se novac prenosi (19 cifara).
     */
    @NotBlank(message = "Unesi racun primaoca")
    @Pattern(regexp = "^\\d{19}$", message = "Broj racuna mora imati 19 cifara")
    private String toAccountNumber;

    /**
     * Iznos koji se prenosi iz izvornog računa u njegovoj valuti.
     * <p>
     * Ako su računi u različitim valutama, ovaj iznos se konvertuje
     * prema toAmount.
     */
    @NotNull(message = "Unesi iznos pre konverzije")
    @DecimalMin(value = "0.0", inclusive = false, message = "Iznos pre konverzije mora biti veci od 0")
    private BigDecimal fromAmount;

    /**
     * Iznos koji se prima na odredišnom računu nakon konverzije (ako je primenjena).
     * <p>
     * Ako su računi u istoj valuti, ova vrednost je jednaka fromAmount.
     * Ako su u različitim valutama, ova vrednost je konvertovana prema
     * kursnim paritetu.
     */
    @NotNull(message = "Unesi iznos posle konverzije")
    @DecimalMin(value = "0.0", inclusive = false, message = "Iznos posle konverzije mora biti veci od 0")
    private BigDecimal toAmount;

    /**
     * Komisija za transakciju. Obično se oduzima od izvornog računa.
     */
    @NotNull
    @DecimalMin(value = "0.00",message = "Minimalni commission je 0")
    private BigDecimal commission;

    /**
     * ID klijenta koji inicira transfer (za audit log).
     */
    @NotNull(message = "Unesi id clienta")
    private Long clientId;
}
