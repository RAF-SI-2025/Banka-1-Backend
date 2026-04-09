package com.banka1.credit_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO za zahtev izvršavanja finansijske transakcije ili transfera.
 * <p>
 * Koristi se za intra-bank transfere novca između računa, sa
 * mogućnošću konverzije između različitih valuta i nabijanjem komisije.
 * <p>
 * Validacija:
 * <ul>
 *   <li>Oba broja računa moraju biti 19-cifreni</li>
 *   <li>Iznosi (fromAmount i toAmount) moraju biti pozitivni</li>
 *   <li>Komisija mora biti >= 0</li>
 * </ul>
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PaymentDto {
    /**
     * Broj računa sa kojeg se novac prenosi (19 cifara).
     */

    private String fromAccountNumber;

    /**
     * Broj računa na koji se novac prenosi (19 cifara).
     */

    private String toAccountNumber;

    /**
     * Iznos koji se prenosi iz izvornog računa u njegovoj valuti.
     * <p>
     * Ako su računi u različitim valutama, ovaj iznos se konvertuje
     * prema toAmount.
     */

    private BigDecimal fromAmount;

    /**
     * Iznos koji se prima na odredišnom računu nakon konverzije (ako je primenjena).
     * <p>
     * Ako su računi u istoj valuti, ova vrednost je jednaka fromAmount.
     * Ako su u različitim valutama, ova vrednost je konvertovana prema
     * kursnim paritetu.
     */

    private BigDecimal toAmount;

    /**
     * Komisija za transakciju. Obično se oduzima od izvornog računa.
     */

    private BigDecimal commission;

    /**
     * ID klijenta koji inicira transfer (opciono, za audit log).
     */



    private Long clientId;


}
