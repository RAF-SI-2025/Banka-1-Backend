package com.banka1.account_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class EditAccountNameDto {
    @NotBlank(message = "Unesi accountName")
    //@Size(min = 3, max = 50)
    private String accountName;
}
