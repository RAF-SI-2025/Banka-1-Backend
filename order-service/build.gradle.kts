import org.gradle.api.tasks.testing.Test

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
    checkstyle
}

val openApiPort = System.getenv("OPENAPI_PORT")
    ?: System.getenv("ORDER_SERVER_PORT")
    ?: "8088"

group = "com.banka1"
version = "0.0.1-SNAPSHOT"
description = "Order service for managing actuaries, orders, portfolio and tax"

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
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.library:company-observability-starter:0.0.1-SNAPSHOT")
    implementation("com.banka1:security-lib:0.0.1-SNAPSHOT")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
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
    compileOnly("org.projectlombok:lombok")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

jacoco {
    toolVersion = "0.8.12"
}

openApi {
    apiDocsUrl.set("http://localhost:$openApiPort/v3/api-docs.yaml")
    outputDir.set(file("docs"))
    outputFileName.set("openapi.yml")
    waitTimeInSeconds.set(30)
}

checkstyle {
    configFile = rootProject.file("checkstyle.xml")
}
