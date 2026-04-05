package com.banka1.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Order Service microservice.
 * Covers actuary management, order execution, portfolio tracking, and tax collection.
 */
@SpringBootApplication
public class OrderServiceApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
