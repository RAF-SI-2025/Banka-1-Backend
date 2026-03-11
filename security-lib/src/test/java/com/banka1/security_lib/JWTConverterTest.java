package com.banka1.security_lib;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class JWTConverterTest {

    @Test
    void convertsRolesAndPermissionsCorrectly() {
        SecurityProperties props = new SecurityProperties();
        props.setRolesClaim("roles");
        props.setPermissionsClaim("permissions");
        SecurityConfig config = new SecurityConfig();
        JwtAuthenticationConverter converter =
                config.jwtAuthenticationConverter(props);
        Jwt jwt = new Jwt("token",Instant.now(),Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),Map.of("roles", List.of("ADMIN"),
                "permissions", List.of("user.read", "user.write")));
        var authentication = converter.convert(jwt);
        List<String> authorities =
                authentication.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList());
        assertThat(authorities)
                .contains("ROLE_ADMIN")
                .contains("user.read")
                .contains("user.write");
    }
}