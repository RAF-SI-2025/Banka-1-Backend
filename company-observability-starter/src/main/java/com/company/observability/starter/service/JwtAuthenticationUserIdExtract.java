package com.company.observability.starter.service;

import com.company.observability.starter.domain.UserIdExtractor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;

public class JwtAuthenticationUserIdExtract implements UserIdExtractor {
    @Override
    public Optional<String> extractUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || !authentication.isAuthenticated()){
            return Optional.empty();
        }
        return extractFromPrincipal(authentication.getPrincipal())
                .or(() -> extractFromObject(authentication.getDetails()))
                .or(() -> hasText(authentication.getName()) ? Optional.of(authentication.getName()) : Optional.empty());
    }

    private Optional<String> extractFromPrincipal(Object principal) {
        if (principal == null) {
            return Optional.empty();
        }
        return extractFromObject(principal)
                .or(() -> {
                    if (principal instanceof Principal p && hasText(p.getName())) {
                        return Optional.of(p.getName());
                    }
                    return Optional.empty();
                });
    }

    private Optional<String> extractFromObject(Object source) {
        if (source == null) {
            return Optional.empty();
        }
        return extractClaim(source, "userId")
                .or(() -> extractClaim(source, "user_id"))
                .or(() -> extractClaim(source, "uid"))
                .or(() -> extractClaim(source, "sub"))
                .or(() -> extractClaim(source, "preferred_username"));
    }

    private Optional<String> extractClaim(Object source, String claimName) {
        if (source instanceof Map<?, ?> map) {
            Object value = map.get(claimName);
            if (value instanceof String s && hasText(s)) {
                return Optional.of(s);
            }
        }

        try {
            Method getClaimAsString = source.getClass().getMethod("getClaimAsString", String.class);
            Object value = getClaimAsString.invoke(source, claimName);
            if (value instanceof String s && hasText(s)) {
                return Optional.of(s);
            }
        } catch (Exception ignored) {
        }

        try {
            Method getClaims = source.getClass().getMethod("getClaims");
            Object claims = getClaims.invoke(source);
            if (claims instanceof Map<?, ?> map) {
                Object value = map.get(claimName);
                if (value instanceof String s && hasText(s)) {
                    return Optional.of(s);
                }
            }
        } catch (Exception ignored) {
        }

        return Optional.empty();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
