import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "7.1.3"
  kotlin("plugin.spring") version "2.1.10"
  id("org.openapi.generator") version "7.11.0"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.4.0-beta")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.data:spring-data-commons:3.4.3")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.3.1")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.5")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  // Leaving at 2.9.0 to match the version used in App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L16
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.12.0")
  // Leaving at 1.43.0 to match the version used in App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L14
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.46.0")
  implementation("com.google.guava:guava:33.4.0-jre")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.3.1")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.25") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.28")

  testImplementation("org.wiremock:wiremock-standalone:3.12.0")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.testcontainers:localstack:1.20.5")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.782")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    freeCompilerArgs.add("-Xwhen-guards")
  }
}

data class ModelConfiguration(val name: String, val packageName: String, val url: String? = null) {
  fun toBuildModelTaskName(): String = "build${nameToCamel()}ApiModel"
  fun toWriteJsonTaskName(): String = "write${nameToCamel()}Json"
  private val snakeRegex = "-[a-zA-Z]".toRegex()
  private fun nameToCamel(): String = snakeRegex.replace(name) {
    it.value.replace("-", "").uppercase()
  }.replaceFirstChar { it.uppercase() }
  val input: String
    get() = "openapi-specs/$name-api-docs.json"
  val output: String
    get() = name
}

val models = listOf(
  ModelConfiguration(
    name = "activities",
    packageName = "activities",
    url = "https://activities-api-dev.prison.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "adjudications",
    packageName = "adjudications",
    url = "https://manage-adjudications-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "non-associations",
    packageName = "nonassociations",
    url = "https://non-associations-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "locations",
    packageName = "locations",
    url = "https://locations-inside-prison-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "nomis-sync",
    packageName = "nomissync",
  ),
  ModelConfiguration(
    name = "nomis-prisoner",
    packageName = "nomisprisoner",
    url = "https://nomis-prisoner-api-dev.prison.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "nomis-mapping-service",
    packageName = "nomismappings",
    url = "https://nomis-sync-prisoner-mapping-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "sentencing-adjustments",
    packageName = "sentencing.adjustments",
    url = "https://adjustments-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "court-sentencing",
    packageName = "court.sentencing",
    url = "https://remand-and-sentencing-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "alerts",
    packageName = "alerts",
    url = "https://alerts-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "csip",
    packageName = "csip",
    url = "https://csip-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "casenotes",
    packageName = "casenotes",
    url = "https://dev.offender-case-notes.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "personal-relationships",
    packageName = "personalrelationships",
    url = "https://personal-relationships-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "organisations",
    packageName = "organisations",
    url = "https://organisations-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
)

tasks {
  withType<KotlinCompile> {
    dependsOn(models.map { it.toBuildModelTaskName() })
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
  withType<KtLintCheckTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
  withType<KtLintFormatTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
}
models.forEach {
  tasks.register(it.toBuildModelTaskName(), GenerateTask::class) {
    group = "Generate model from API JSON definition"
    description = "Generate model from API JSON definition for ${it.name}"
    generatorName.set("kotlin")
    skipValidateSpec.set(true)
    inputSpec.set(it.input)
    outputDir.set("$buildDirectory/generated/${it.output}")
    modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.${it.packageName}.model")
    apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.${it.packageName}.api")
    configOptions.set(configValues)
    globalProperties.set(mapOf("models" to ""))
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
  }
  it.url?.let { url ->
    tasks.register(it.toWriteJsonTaskName()) {
      group = "Write JSON"
      description = "Write JSON for ${it.name}"
      doLast {
        val json = URI.create(url).toURL().readText()
        val formattedJson = ObjectMapper().let { mapper ->
          mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
        }
        Files.write(Paths.get(it.input), formattedJson.toByteArray())
      }
    }
  }
}

val buildDirectory: Directory = layout.buildDirectory.get()
val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
)

kotlin {
  models.map { it.output }.forEach { generatedProject ->
    sourceSets["main"].apply {
      kotlin.srcDir("$buildDirectory/generated/$generatedProject/src/main/kotlin")
    }
  }
}

configure<KtlintExtension> {
  models.map { it.output }.forEach { generatedProject ->
    filter {
      exclude {
        it.file.path.contains("$buildDirectory/generated/$generatedProject/src/main/")
      }
    }
  }
}
