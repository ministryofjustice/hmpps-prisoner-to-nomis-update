import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.8.0"
  kotlin("plugin.spring") version "1.9.20"
  id("org.openapi.generator") version "7.0.1"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.data:spring-data-commons:3.1.5")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:2.1.1")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.2.0")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:1.31.0")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.18")
  testImplementation("io.jsonwebtoken:jjwt-impl:0.12.3")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.12.3")

  testImplementation("org.wiremock:wiremock:3.2.0")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.testcontainers:localstack:1.19.1")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.578")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<KotlinCompile> {
    dependsOn("buildActivityApiModel", "buildNomisSyncApiModel", "buildAdjudicationApiModel", "buildNonAssociationApiModel", "buildMappingServiceApiModel")
    kotlinOptions {
      jvmTarget = "21"
    }
  }
  withType<KtLintCheckTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter("buildActivityApiModel", "buildNomisSyncApiModel", "buildAdjudicationApiModel", "buildNonAssociationApiModel", "buildMappingServiceApiModel")
  }
  withType<KtLintFormatTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter("buildActivityApiModel", "buildNomisSyncApiModel", "buildAdjudicationApiModel", "buildNonAssociationApiModel", "buildMappingServiceApiModel")
  }
}

val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original"
)

val buildDirectory: Directory = layout.buildDirectory.get()

tasks.register("buildActivityApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true) // TODO - turn this back on when the spec is valid again!
  inputSpec.set("openapi-specs/activities-api-docs.json")
  // remoteInputSpec.set("https://activities-api-dev.prison.service.justice.gov.uk/v3/api-docs")
  outputDir.set("$buildDirectory/generated/activities")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildAdjudicationApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/adjudications-api-docs.json")
  outputDir.set("$buildDirectory/generated/adjudications")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildNonAssociationApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/non-associations-api-docs.json")
  outputDir.set("$buildDirectory/generated/nonassociations")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildNomisSyncApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/nomis-sync-api-docs.json")
  // remoteInputSpec.set("https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/v3/api-docs")
  outputDir.set("$buildDirectory/generated/nomissync")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildMappingServiceApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/nomis-mapping-service-api-docs.json")
  outputDir.set("$buildDirectory/generated/mappings")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api")
  configOptions.set(
    mapOf(
      "dateLibrary" to "java8-localdatetime",
      "serializationLibrary" to "jackson",
      "enumPropertyNaming" to "original"
    )
  )
  globalProperties.set(mapOf("models" to ""))
}

val generatedProjectDirs = listOf("activities", "adjudications", "nonassociations", "nomissync", "mappings")

kotlin {
  generatedProjectDirs.forEach { generatedProject ->
    sourceSets["main"].apply {
      kotlin.srcDir("$buildDirectory/generated/$generatedProject/src/main/kotlin")
    }
  }
}

configure<KtlintExtension> {
  filter {
    generatedProjectDirs.forEach { generatedProject ->
      exclude { element ->
        element.file.path.contains("build/generated/$generatedProject/src/main/")
      }
    }
  }
}
