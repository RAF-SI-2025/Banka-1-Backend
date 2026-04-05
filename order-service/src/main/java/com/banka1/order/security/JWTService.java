package com.banka1.order.security;

/**
 * Service interface for generating service-to-service JWT tokens.
 * The generated token carries the "SERVICE" role and is used by
 * RestClient interceptors when calling other microservices.
 */
public interface JWTService {

    /**
     * Generates a signed JWT token with the SERVICE role claim.
     *
     * @return serialized JWT string ready for use in an Authorization header
     */
    String generateJwtToken();
}
