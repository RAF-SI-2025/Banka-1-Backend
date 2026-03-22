package com.banka1.verificationService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Osnovni test koji proverava da li se Spring kontekst uspesno ucitava.
 */
@SpringBootTest
@ActiveProfiles("test")
class VerificationServiceApplicationTests {

    /**
     * Proverava da li se aplikacioni kontekst uspesno ucitava.
     */
    @Test
    void contextLoads() {
    }
}
