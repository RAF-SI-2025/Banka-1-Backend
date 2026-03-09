package com.company.observability.starter.domain;

import java.util.Optional;

public interface UserIdExtractor {
    Optional<String> extractUserId();
}