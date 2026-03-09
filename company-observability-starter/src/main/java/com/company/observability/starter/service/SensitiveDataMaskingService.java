package com.company.observability.starter.service;

import java.util.regex.Pattern;

// Servis za maskiranje osetljivih podataka pre logovanja
public class SensitiveDataMaskingService {
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("(?i)(password=)([^&\\s]+)");

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("(?i)(token=)([^&\\s]+)");

    public String maskQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        String masked = PASSWORD_PATTERN.matcher(query).replaceAll("$1***");
        masked = TOKEN_PATTERN.matcher(masked).replaceAll("$1***");

        return masked;
    }

    public String maskAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }

        return "***";
    }
}
