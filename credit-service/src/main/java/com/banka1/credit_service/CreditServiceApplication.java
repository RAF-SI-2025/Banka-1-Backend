package com.banka1.credit_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CreditServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CreditServiceApplication.class, args);
	}

}
