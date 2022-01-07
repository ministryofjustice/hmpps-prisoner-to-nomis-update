plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.0.1-beta"
  kotlin("plugin.spring") version "1.6.10"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  implementation("org.springdoc:springdoc-openapi-ui:1.6.4")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.4")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.4")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.0.29")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.mockito:mockito-inline:4.2.0")
  testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "17"
    }
  }
}
