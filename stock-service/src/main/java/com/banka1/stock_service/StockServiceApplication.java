package com.banka1.stock_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main entry point for {@code stock-service}.
 * Starts the Spring Boot application and enables scanning
 * of {@code @ConfigurationProperties} classes inside the module.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class StockServiceApplication {

    /**
     * Starts the application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(StockServiceApplication.class, args);
    }
}
