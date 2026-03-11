package com.banka1.userService.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class TokenResponseDto {
    private String jwt;
    private String refreshToken;
}
