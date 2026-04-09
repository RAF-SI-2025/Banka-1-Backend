package com.banka1.transaction_service.security;

/**
 * Interface for a service that generates JWT tokens for authentication.
 * Used for communication between mixers.
 */
public interface JWTService {
    /**
     * Generates a JWT access token with standard claims.
     * @return the serialized signed JWT token as a string
     *
     * @return the generated JWT
     */
    String generateJwtToken();


}
