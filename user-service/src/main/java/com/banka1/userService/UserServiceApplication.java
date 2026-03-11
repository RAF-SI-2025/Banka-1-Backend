package com.banka1.userService;

import com.banka1.userService.configuration.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(AppProperties.class)
@SpringBootApplication
public class UserServiceApplication {

	/**
	 * Pokrece Spring Boot aplikaciju.
	 *
	 * @param args argumenti komandne linije
	 */
	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
