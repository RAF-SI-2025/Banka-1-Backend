plugins {
	id("java-library")
	id("maven-publish")
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.banka1"
version = "0.0.1-SNAPSHOT"
description = "Library for authorizing users of BANKA1 system"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}
dependencyManagement {
//	imports {
//		mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.3")
//	}
}

dependencies {
	api(platform("org.springframework.boot:spring-boot-dependencies:3.4.3"))
	api("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.4.3")
//	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	testImplementation("org.springframework.security:spring-security-test")
//	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-web")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")


}

tasks.withType<Test> {
	useJUnitPlatform()
}
