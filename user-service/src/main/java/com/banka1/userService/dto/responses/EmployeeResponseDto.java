package com.banka1.userService.dto.responses;

import com.banka1.userService.domain.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class EmployeeResponseDto {
    private Long id;
    private String ime;
    private String prezime;
    private String email;
    private String username;
    private String pozicija;
    private String departman;
    private boolean aktivan;
    private Role role;
}
