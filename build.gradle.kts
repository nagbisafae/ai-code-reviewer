plugins {
	java
	id("org.springframework.boot") version "3.5.8"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.codereview"
version = "0.0.1-SNAPSHOT"
description = "ai-code-reviewer"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
}

extra["springAiVersion"] = "1.1.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	developmentOnly("org.springframework.boot:spring-boot-devtools")

	// Google GenAI for Vertex AI
	implementation("org.springframework.ai:spring-ai-starter-model-google-genai")

	// JavaParser for code analysis
	implementation("com.github.javaparser:javaparser-symbol-solver-core:3.25.8")

	// GitHub App authentication
	implementation ("io.jsonwebtoken:jjwt-api:0.12.3")
	runtimeOnly ("io.jsonwebtoken:jjwt-impl:0.12.3")
	runtimeOnly ("io.jsonwebtoken:jjwt-jackson:0.12.3")

	// For reading PEM files
	implementation ("org.bouncycastle:bcpkix-jdk18on:1.78")

	implementation("org.apache.httpcomponents.client5:httpclient5")

	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}