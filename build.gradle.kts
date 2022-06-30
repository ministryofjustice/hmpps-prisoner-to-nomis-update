plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.3.1"
  kotlin("plugin.spring") version "1.7.0"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.5")

  implementation("org.springdoc:springdoc-openapi-ui:1.6.9")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.9")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.9")
  implementation("org.springdoc:springdoc-openapi-security:1.6.9")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.3")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.1")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.mockito:mockito-inline:4.6.1")
  testImplementation("org.testcontainers:localstack:1.17.3")
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
