plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	kotlin("plugin.jpa") version "2.3.21"
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
}

// Versions not managed by the Spring Boot BOM (Constitution V: declared centrally here).
// OTel instrumentation 2.28.x pins the OTel SDK 1.62.0 that Spring Boot 4.1.2 manages.
val openTelemetryInstrumentationVersion = "2.28.1"
// datasource-micrometer 2.3.x is built against Spring Boot 4.1.
val datasourceMicrometerVersion = "2.3.0"

group = "com.spec"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		// The alpha BOM imports the stable one and also manages alpha artifacts such as the Logback appender.
		mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:$openTelemetryInstrumentationVersion-alpha")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
	implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0")
	implementation("net.ttddyy.observation:datasource-micrometer-spring-boot:$datasourceMicrometerVersion")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	runtimeOnly("org.postgresql:postgresql")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.testcontainers:testcontainers-grafana")
	testImplementation("io.micrometer:micrometer-tracing-test")
	testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform {
		// End-to-end tests (real Grafana LGTM stack) are slow; run them with -Pe2e.
		if (!project.hasProperty("e2e")) excludeTags("e2e")
	}
	systemProperty("spring.profiles.active", "test")
}
