package com.banka1.userService.domain.service.implementation;

import com.banka1.userService.configuration.AppProperties;
import com.banka1.userService.domain.Zaposlen;
import com.banka1.userService.domain.enums.Role;
import com.banka1.userService.domain.service.ZaposlenService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@AllArgsConstructor
@Getter
@Setter
public class ZaposlenServiceImplementation implements ZaposlenService {
    private AppProperties appProperties;
    @Override
    public void setovanjePermisija(Zaposlen zaposlen) {
        Role[] roles=Role.values();
        for(int i=0;i<zaposlen.getRole().getPower();i++)
        {
            zaposlen.getPermissionSet().addAll(appProperties.getPermissions().get(roles[i]));
        }
    }
}
