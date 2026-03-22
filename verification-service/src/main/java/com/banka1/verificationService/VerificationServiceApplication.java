package com.banka1.verificationService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point za Verification Service.
 * Ovaj servis upravlja 2FA verifikacionim sesijama za potvrdu transakcija klijenata
 * (placanja, transferi, promene limita, zahtevi za kartice/kredite).
 */
@SpringBootApplication
public class VerificationServiceApplication {

	/**
	 * Pokretanje Spring Boot aplikacije.
	 *
	 * @param args argumenti komandne linije
	 */
	public static void main(String[] args) {
		SpringApplication.run(VerificationServiceApplication.class, args);
	}
}
