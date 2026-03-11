package com.banka1.userService.dto.rabbitmq;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@Getter
@Setter
public class ResetLinkDto {
    private String resetLink;
    private String activationLink;
    /**
     * Popunjava odgovarajuce polje za link na osnovu tipa email poruke.
     *
     * @param email link za reset lozinke ili aktivaciju naloga
     * @param emailType tip mejla koji odredjuje koje polje se popunjava
     */
    public ResetLinkDto(String email,EmailType emailType)
    {
        switch (emailType)
        {
            case EmailType.PASSWORD_RESET -> resetLink=email;
            case EmailType.ACTIVATION -> activationLink=email;
            default -> throw new IllegalStateException("Kako si ovo uspeo majke ti");
        }
    }
}
