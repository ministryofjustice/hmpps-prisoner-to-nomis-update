import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.Path as KotlinPath

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "10.0.4"
  kotlin("plugin.spring") version "2.3.10"
  id("org.openapi.generator") version "7.20.0"
}

configurations {
  implementation {
    exclude(module = "spring-boot-starter-web")
    exclude(module = "spring-boot-starter-tomcat")
  }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:2.0.1")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.data:spring-data-commons")
  implementation("org.springframework.security:spring-security-access")
  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.1")
  implementation("org.springframework.boot:spring-boot-jackson2")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.0.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  // Needs to match this version https://github.com/microsoft/ApplicationInsights-Java/blob/<version>/dependencyManagement/build.gradle.kts#L16
  // where <version> is the version of application insights pulled in by hmpps-gradle-spring-boot
  // at https://github.com/ministryofjustice/hmpps-gradle-spring-boot/blob/main/src/main/kotlin/uk/gov/justice/digital/hmpps/gradle/configmanagers/AppInsightsConfigManager.kt#L7
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.21.0")
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.55.0")
  implementation("com.google.guava:guava:33.5.0-jre")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:2.0.1")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.37") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.41")

  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.testcontainers:localstack:1.21.4")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.796")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

kotlin {
  jvmToolchain(25)
  compilerOptions {
    freeCompilerArgs.addAll("-Xwhen-guards", "-Xannotation-default-target=param-property")
  }
}

data class ModelConfiguration(val name: String, val packageName: String, val testPackageName: String? = null, val url: String, val models: String = "") {
  fun toBuildModelTaskName(): String = "build${nameToCamel()}ApiModel"
  fun toWriteJsonTaskName(): String = "write${nameToCamel()}Json"
  fun toReadProductionVersionTaskName(): String = "read${nameToCamel()}ProductionVersion"
  fun toTestTaskName(): String = "test${nameToCamel()}"
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
    models = "Activity,ActivityCategory,ActivityEligibility,ActivityLite,ActivityMinimumEducationLevel,ActivityPay,ActivityPayHistory,ActivitySchedule,ActivityScheduleInstance,ActivityScheduleLite,ActivityScheduleSlot,AdvanceAttendance,AdvanceAttendanceHistory,Allocation,AllocationReconciliationResponse,AppointmentAttendeeSearchResult,AppointmentCategorySummary,AppointmentInstance,AppointmentLocationSummary,AppointmentSearchRequest,AppointmentSearchResult,Attendance,AttendanceHistory,AttendanceReason,AttendanceReconciliationResponse,AttendanceSync,BookingCount,DeallocationReason,EarliestReleaseDate,EligibilityRule,EventOrganiser,EventTier,InternalLocation,PlannedDeallocation,PlannedSuspension,PrisonPayBand,RolloutPrisonPlan,ScheduledInstance,Slot,Suspension",
  ),
  ModelConfiguration(
    name = "adjudications",
    packageName = "adjudications",
    testPackageName = "adjudications",
    url = "https://manage-adjudications-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "CombinedOutcomeDto,DisIssueHistoryDto,HearingDto,HearingOutcomeDto,IncidentDetailsDto,IncidentRoleDto,IncidentStatementDto,OffenceDto,OffenceRuleDto,OffenceRuleDetailsDto,OutcomeDto,OutcomeHistoryDto,PunishmentDto,PunishmentCommentDto,PunishmentScheduleDto,RehabilitativeActivityDto,ReportedAdjudicationDto,ReportedAdjudicationResponse,ReportedDamageDto,ReportedEvidenceDto,ReportedWitnessDto",
  ),
  ModelConfiguration(
    name = "alerts",
    packageName = "alerts",
    testPackageName = "alerts",
    url = "https://alerts-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "Alert,AlertCode,AlertCodeSummary,AlertType",
  ),
  ModelConfiguration(
    name = "casenotes",
    packageName = "casenotes",
    url = "https://dev.offender-case-notes.service.justice.gov.uk/v3/api-docs",
    models = "CaseNote,CaseNoteAmendment",
  ),
  ModelConfiguration(
    name = "core-person",
    packageName = "coreperson",
    url = "https://hmpps-person-record-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "court-sentencing",
    packageName = "court.sentencing",
    testPackageName = "courtsentencing",
    url = "https://remand-and-sentencing-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "csip",
    packageName = "csip",
    url = "https://csip-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "Attendee,ContributoryFactor,CsipRecord,DecisionAndActions,IdentifiedNeed,Interview,Investigation,Plan,ReferenceData,Referral,Review,SaferCustodyScreeningOutcome",
  ),
  ModelConfiguration(
    name = "finance",
    packageName = "finance",
    testPackageName = "finance",
    url = "https://prisoner-finance-sync-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "incidents",
    packageName = "incidents",
    url = "https://incident-reporting-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "CorrectionRequest,DescriptionAddendum,HistoricalQuestion,HistoricalResponse,History,IncidentTypeHistory,NomisCode,NomisHistory,NomisHistoryQuestion,NomisHistoryResponse,NomisOffender,NomisOffenderParty,NomisQuestion,NomisReport,NomisRequirement,NomisResponse,NomisStaff,NomisStaffParty,NomisStatus,NomisSyncReportId,NomisSyncRequest,PairStringListDescriptionAddendum,PrisonerInvolvement,Question,ReportBasic,ReportWithDetails,Response,SimplePageReportBasic,StaffInvolvement,StatusHistory",
  ),
  ModelConfiguration(
    name = "incentives",
    packageName = "incentives",
    url = "https://incentives-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "movements",
    packageName = "movements",
    testPackageName = "movements",
    url = "https://external-movements-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "non-associations",
    packageName = "nonassociations",
    url = "https://non-associations-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "LegacyNonAssociation,LegacyNonAssociationOtherPrisonerDetails,NonAssociation",
  ),
  ModelConfiguration(
    name = "locations",
    packageName = "locations",
    url = "https://locations-inside-prison-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "Capacity,Certification,ChangeHistory,LegacyLocation,NonResidentialUsageDto,PatchNonResidentialLocationRequest",
  ),
  ModelConfiguration(
    name = "nomis-mapping-service",
    packageName = "nomismappings",
    url = "https://nomis-sync-prisoner-mapping-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "nomis-prisoner",
    packageName = "nomisprisoner",
    url = "https://nomis-prisoner-api-dev.prison.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "organisations",
    packageName = "organisations",
    testPackageName = "organisations",
    url = "https://organisations-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "personal-relationships",
    packageName = "personalrelationships",
    testPackageName = "personalrelationships",
    url = "https://personal-relationships-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "sentencing-adjustments",
    packageName = "sentencing.adjustments",
    url = "https://adjustments-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "visit-balance",
    packageName = "visit.balance",
    url = "https://hmpps-visit-allocation-api-dev.prison.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "officialvisits",
    packageName = "officialvisits",
    testPackageName = "officialvisits",
    url = "https://official-visits-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
)

tasks {
  withType<KotlinCompile> {
    dependsOn(models.map { it.toBuildModelTaskName() })
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
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
val separateTestPackages = mutableListOf<String>()
models.forEach {
  tasks.register(it.toBuildModelTaskName(), GenerateTask::class) {
    group = "Generate model from API JSON definition"
    description = "Generate model from API JSON definition for ${it.name}"
    generatorName.set("kotlin")
    library.set("jvm-spring-webclient")
    skipValidateSpec.set(true)
    inputSpec.set(it.input)
    outputDir.set("$buildDirectory/generated/${it.output}")
    modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.${it.packageName}.model")
    apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.${it.packageName}.api")
    configOptions.set(configValues)
    KotlinPath("openapi-generator-ignore-${it.name}")
      .takeIf { p -> p.exists() }?.apply { ignoreFileOverride.set(this.name) }
      ?: globalProperties.set(mapOf("models" to it.models))
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
  }
  tasks.register(it.toWriteJsonTaskName()) {
    group = "Write JSON"
    description = "Write JSON for ${it.name}"
    doLast {
      val json = URI.create(it.url).toURL().readText()
      val formattedJson = JsonMapper().let { mapper ->
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
      }
      Files.write(Paths.get(it.input), formattedJson.toByteArray())
    }
  }
  if (it.name != "finance") {
    tasks.register(it.toReadProductionVersionTaskName()) {
      group = "Read current production version"
      description = "Read current production version for ${it.name}"
      doLast {
        val productionUrl = it.url.replace("-dev".toRegex(), "")
          .replace("dev.".toRegex(), "")
          .replace("/v3/api-docs".toRegex(), "/info")
        val json = URI.create(productionUrl).toURL().readText()
        val version = JsonMapper().readTree(json).at("/build/version").asString()
        println(version)
      }
    }
  } else {
    tasks.register(it.toReadProductionVersionTaskName()) {
      group = "Read current production version"
      description = "Read current production version for ${it.name}"
      doLast {
        println("no production version")
      }
    }
  }
  if (it.testPackageName != null) {
    separateTestPackages.add(it.testPackageName)
    val test by testing.suites.existing(JvmTestSuite::class)
    val task = tasks.register<Test>(it.toTestTaskName()) {
      testClassesDirs = files(test.map { it.sources.output.classesDirs })
      classpath = files(test.map { it.sources.runtimeClasspath })
      group = "Run tests"
      description = "Run tests for ${it.name}"
      shouldRunAfter("test")
      useJUnitPlatform()
      filter {
        includeTestsMatching("uk.gov.justice.digital.hmpps.prisonertonomisupdate.${it.testPackageName}.*")
      }
    }
    tasks.check { dependsOn(task) }
  }
}

tasks.test {
  filter {
    separateTestPackages.forEach {
      excludeTestsMatching("uk.gov.justice.digital.hmpps.prisonertonomisupdate.$it.*")
    }
  }
  maxHeapSize = "1024m"
}

val buildDirectory: Directory = layout.buildDirectory.get()
val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
  "useSpringBoot3" to "true",
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
