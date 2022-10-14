plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.5.5"
  kotlin("plugin.spring") version "1.7.10"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.12")

  implementation("org.springdoc:springdoc-openapi-ui:1.6.11")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.11")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.11")
  implementation("org.springdoc:springdoc-openapi-security:1.6.11")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.3")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.mockito:mockito-inline:4.8.0")
  testImplementation("org.testcontainers:localstack:1.17.5")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(18))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "18"
    }
  }
}
