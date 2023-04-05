import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.1.3-beta-2"
  kotlin("plugin.spring") version "1.8.10"
  id("org.openapi.generator") version "6.4.0"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

repositories {
  maven { url = uri("https://repo.spring.io/milestone") }
  mavenCentral()
}
dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:2.0.0-beta-13")

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.0.4")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:1.24.0")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.13")
  testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:2.35.0")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.testcontainers:localstack:1.17.6")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.440")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(19))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn("buildActivityApiModel")
    kotlinOptions {
      jvmTarget = "19"
    }
  }
}

tasks.register("buildActivityApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("activities-api-docs.json")
  // remoteInputSpec.set("https://activities-api-dev.prison.service.justice.gov.uk/v3/api-docs")
  outputDir.set("$buildDir/generated")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.api")
  configOptions.set(
    mapOf(
      "dateLibrary" to "java8-localdatetime",
      "serializationLibrary" to "jackson"
    )
  )
  globalProperties.set(
    mapOf(
      "models" to ""
    )
  )
}

kotlin {
  sourceSets["main"].apply {
    kotlin.srcDir("$buildDir/generated/src/main/kotlin")
  }
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
  filter {
    exclude {
      it.file.path.contains("build/generated/src/main/")
    }
  }
}
