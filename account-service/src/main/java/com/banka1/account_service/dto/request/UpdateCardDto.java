package com.banka1.account_service.dto.request;

import com.banka1.account_service.domain.enums.CardStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UpdateCardDto {
    @NotNull(message = "Unesi zeljeni status")
    private CardStatus cardStatus;
}
