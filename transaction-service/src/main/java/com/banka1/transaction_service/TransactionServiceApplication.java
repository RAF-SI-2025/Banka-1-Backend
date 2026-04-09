package com.banka1.transaction_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application for the Transaction Service.
 * Enables management of financial transactions and money transfers between accounts.
 * <p>
 * Includes scheduled tasks via the @EnableScheduling annotation.
 */
@SpringBootApplication
@EnableScheduling
public class TransactionServiceApplication {

	/**
	 * Entry point of the application.
	 *
	 * @param args command-line arguments passed to the application
	 */
	public static void main(String[] args) {
		SpringApplication.run(TransactionServiceApplication.class, args);
	}

}
