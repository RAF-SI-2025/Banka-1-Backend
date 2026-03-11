plugins {
	java
	id("org.springframework.boot") version "4.0.3"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.banka1"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
//	mavenLocal()
	mavenCentral()
}

dependencies {
	implementation("com.banka1:security-lib:0.0.1-SNAPSHOT")
//	implementation("tools.jackson.core:jackson-core:2.21.1")
//	implementation("tools.jackson.core:jackson-databind:2.21.1")
//	implementation("tools.jackson.core:jackson-annotations:2.21.1")
	implementation ("org.springframework.boot:spring-boot-starter-actuator")
	implementation("me.paulschwarz:springboot3-dotenv:5.0.1")
	implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
	implementation("org.springframework.boot:spring-boot-starter-amqp")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-liquibase")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.core:jackson-core:2.21.1")
	implementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
	implementation("com.fasterxml.jackson.core:jackson-annotations:2.21")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-amqp-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-liquibase-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("com.h2database:h2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
