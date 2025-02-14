import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "7.1.2"
  kotlin("plugin.spring") version "2.1.10"
  id("org.openapi.generator") version "7.11.0"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.2.0")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.data:spring-data-commons:3.4.2")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.3.0")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.4")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  // Leaving at 2.9.0 to match the version used in App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L16
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.12.0")
  // Leaving at 1.43.0 to match the version used in App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L14
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.46.0")
  implementation("com.google.guava:guava:33.4.0-jre")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.25") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.28")

  testImplementation("org.wiremock:wiremock-standalone:3.12.0")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.testcontainers:localstack:1.20.4")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.781")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.2")
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
      "buildAlertsApiModel",
      "buildCsipApiModel",
      "buildCaseNoteApiModel",
      "buildPrisonPersonApiModel",
      "buildPersonalRelationshipsApiModel",
      "buildOrganisationsApiModel",
    )
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
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
      "buildAlertsApiModel",
      "buildCsipApiModel",
      "buildCaseNoteApiModel",
      "buildPrisonPersonApiModel",
      "buildPersonalRelationshipsApiModel",
      "buildOrganisationsApiModel",
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
      "buildAlertsApiModel",
      "buildCsipApiModel",
      "buildCaseNoteApiModel",
      "buildPrisonPersonApiModel",
      "buildPersonalRelationshipsApiModel",
      "buildOrganisationsApiModel",
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
  inputSpec.set("openapi-specs/activities-api-docs.json")
  // remoteInputSpec.set("https://activities-api-dev.prison.service.justice.gov.uk/v3/api-docs")
  outputDir.set("$buildDirectory/generated/activities")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildAdjudicationApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true) // TODO - turn validation back on when the spec is valid again!
  inputSpec.set("openapi-specs/adjudications-api-docs.json")
  outputDir.set("$buildDirectory/generated/adjudications")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildNonAssociationApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/non-associations-api-docs.json")
  outputDir.set("$buildDirectory/generated/nonassociations")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildLocationApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/locations-api-docs.json")
  outputDir.set("$buildDirectory/generated/locations")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildNomisSyncApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/nomis-sync-api-docs.json")
  // remoteInputSpec.set("https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/v3/api-docs")
  outputDir.set("$buildDirectory/generated/nomissync")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
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
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildSentencingAdjustmentsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/sentencing-adjustments-api-docs.json")
  outputDir.set("$buildDirectory/generated/sentencingadjustments")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildCourtSentencingApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/court-sentencing-api-docs.json")
  outputDir.set("$buildDirectory/generated/courtsentencing")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildAlertsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/alerts-api-docs.json")
  outputDir.set("$buildDirectory/generated/alerts")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildCsipApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/csip-api-docs.json")
  outputDir.set("$buildDirectory/generated/csip")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildCaseNoteApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/casenotes-api-docs.json")
  outputDir.set("$buildDirectory/generated/casenotes")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildPrisonPersonApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/prison-person-api-docs.json")
  outputDir.set("$buildDirectory/generated/prisonperson")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildContactPersonApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/contact-person-api-docs.json")
  outputDir.set("$buildDirectory/generated/contactperson")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildPersonalRelationshipsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/personal-relationships-api-docs.json")
  outputDir.set("$buildDirectory/generated/personalrelationships")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
}

tasks.register("buildOrganisationsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/organisations-api-docs.json")
  outputDir.set("$buildDirectory/generated/organisations")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
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
  "casenotes",
  "csip",
  "prisonperson",
  "personalrelationships",
  "organisations",
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
