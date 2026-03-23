package com.banka1.transaction_service.domain;

import com.banka1.transaction_service.domain.base.BaseEntityWithoutDelete;
import com.banka1.transaction_service.domain.enums.TransactionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;


//Ideja sa BaseEntityWithoutDelete je da se ohrabri dobar dizajn pattern,
  //ukoliko ispadne da je Payment jedini sa ovim, obrisacu ga

@Entity
@Table(
        name = "payment_table"
)

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Payment extends BaseEntityWithoutDelete {
    @NotBlank
    @Column(nullable = false,unique = true)
    private String orderNumber;
    @NotBlank
    @Column(nullable = false)
    private String fromAccountNumber;
    @NotBlank
    @Column(nullable = false)
    private String toAccountNumber;
    @Column(nullable = false)
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal initialAmount;
    @Column(nullable = false)
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal finalAmount;
    @Column(nullable = false)
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal commission;
    @Column(nullable = false)
    private Long recipientClientId;
    @NotBlank
    @Column(nullable = false)
    private String recipientName;
    @NotBlank
    @Pattern(regexp = "^2.*", message = "Sifra mora poceti sa 2")
    @Pattern(regexp = "^\\d{3}$", message = "Sifra mora imati tacno 3 cifre")
    @Column(nullable = false)
    private String paymentCode;
    private String referenceNumber;
    @NotBlank
    @Column(nullable = false)
    private String paymentPurpose;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status=TransactionStatus.IN_PROGRESS;
    @NotBlank
    @Column(nullable = false)
    private String fromCurrency;
    @NotBlank
    @Column(nullable = false)
    private String toCurrency;
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal exchangeRate;





}
