import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.15.6"
  kotlin("plugin.spring") version "1.9.23"
  id("org.openapi.generator") version "7.5.0"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:0.2.4")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.data:spring-data-commons:3.2.5")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:3.1.3")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.5.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:1.33.1")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.22") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.21")
  testImplementation("io.jsonwebtoken:jjwt-impl:0.12.5")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.12.5")

  testImplementation("org.wiremock:wiremock-standalone:3.5.4")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.testcontainers:localstack:1.19.7")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.710")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<KotlinCompile> {
    dependsOn(
      "buildActivityApiModel",
      "buildNomisSyncApiModel",
      "buildAdjudicationApiModel",
      "buildNonAssociationApiModel",
      "buildLocationApiModel",
      "buildMappingServiceApiModel",
      "buildSentencingAdjustmentsApiModel",
      "buildCourtSentencingApiModel",
      "buildAlertApiModel",
    )
    kotlinOptions {
      jvmTarget = "21"
    }
  }
  withType<KtLintCheckTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(
      "buildActivityApiModel",
      "buildNomisSyncApiModel",
      "buildAdjudicationApiModel",
      "buildNonAssociationApiModel",
      "buildLocationApiModel",
      "buildMappingServiceApiModel",
      "buildSentencingAdjustmentsApiModel",
      "buildCourtSentencingApiModel",
      "buildAlertApiModel",
    )
  }
  withType<KtLintFormatTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(
      "buildActivityApiModel",
      "buildNomisSyncApiModel",
      "buildAdjudicationApiModel",
      "buildNonAssociationApiModel",
      "buildLocationApiModel",
      "buildMappingServiceApiModel",
      "buildSentencingAdjustmentsApiModel",
      "buildCourtSentencingApiModel",
      "buildAlertApiModel",
    )
  }
}

val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
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

tasks.register("buildLocationApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/locations-api-docs.json")
  outputDir.set("$buildDirectory/generated/locations")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.api")
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
      "enumPropertyNaming" to "original",
    ),
  )
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildSentencingAdjustmentsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/sentencing-adjustments-api-docs.json")
  outputDir.set("$buildDirectory/generated/sentencingadjustments")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildCourtSentencingApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/court-sentencing-api-docs.json")
  outputDir.set("$buildDirectory/generated/courtsentencing")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildAlertApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/alerts-api-docs.json")
  outputDir.set("$buildDirectory/generated/alerts")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

val generatedProjectDirs = listOf(
  "activities",
  "adjudications",
  "nonassociations",
  "locations",
  "nomissync",
  "mappings",
  "sentencingadjustments",
  "courtsentencing",
  "alerts",
)

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
