package com.banka1.userService.dto.responses;

import com.banka1.userService.domain.enums.Permission;
import com.banka1.userService.domain.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@AllArgsConstructor
@Getter
@Setter
public class TokenResponseDto {
    private String jwt;
    private String refreshToken;
    private Role role;
    private Set<Permission>permissions;
}
