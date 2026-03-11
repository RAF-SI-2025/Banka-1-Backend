package com.banka1.userService.configuration;

import com.banka1.userService.domain.enums.Permission;
import com.banka1.userService.domain.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "employees")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AppProperties {
    private Map<Role, List<Permission>>permissions;
}
