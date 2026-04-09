package com.banka1.credit_service.dto.response;

import com.banka1.credit_service.domain.Installment;
import com.banka1.credit_service.domain.Loan;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class LoanInfoResponseDto {
    private LoanResponseDto loan;
    private List<InstallmentResponseDto> installments;
}
