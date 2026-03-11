package com.banka1.userService.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ActivateDto {
    @NotNull(message = "Id ne sme da bude null")
    private Long id;
    @NotBlank(message = "ConfirmationToken ne sme da bude prazan")
    @Size(min = 43, max = 43,message = "Pogresan token")
    private String confirmationToken;
    @NotBlank(message = "Sifra ne sme da bude prazna")
    @Size(min = 8, max = 32,message = "Velicina mora biti izmedju 8 i 32")
    @Pattern(
            regexp = "^(?=(?:.*\\d){2,})(?=.*[a-z])(?=.*[A-Z])(?=\\S+$).*$",
            message = "Password mora imati najmanje 2 broja, 1 veliko i 1 malo slovo, bez razmaka"
    )
    private String password;
}
