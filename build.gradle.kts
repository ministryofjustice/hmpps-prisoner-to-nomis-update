@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.Path as KotlinPath

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "10.3.1"
  kotlin("plugin.spring") version "2.3.21"
  id("org.openapi.generator") version "7.22.0"
}

configurations {
  implementation {
    exclude(module = "spring-boot-starter-web")
    exclude(module = "spring-boot-starter-tomcat")
  }
}

dependencyCheck {
  suppressionFiles.add("azure-dependency-check-suppress.xml")
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:2.2.0")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.data:spring-data-commons")
  implementation("org.springframework.security:spring-security-access")
  // Temporarily pin spring doc at 3.0.2 whilst waiting for 3.0.4 upgrade
  val springDocVersion = ":3.0.2"
  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui$springDocVersion")
  implementation("org.springdoc:springdoc-openapi-starter-common$springDocVersion")
  constraints {
    implementation("org.webjars:swagger-ui:5.32.2")
  }
  implementation("org.springframework.boot:spring-boot-jackson2")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.3.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  // Needs to match this version https://github.com/microsoft/ApplicationInsights-Java/blob/<version>/dependencyManagement/build.gradle.kts#L16
  // where <version> is the version of application insights pulled in by hmpps-gradle-spring-boot
  // at https://github.com/ministryofjustice/hmpps-gradle-spring-boot/blob/main/src/main/kotlin/uk/gov/justice/digital/hmpps/gradle/configmanagers/AppInsightsConfigManager.kt#L7
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.26.1")
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.60.1")
  implementation("com.google.guava:guava:33.6.0-jre")

  val appinsightsCore = "core:2.6.4"
  implementation("io.micrometer:micrometer-registry-azure-monitor:1.16.5")
  implementation("com.microsoft.azure:applicationinsights-$appinsightsCore")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:2.2.0")
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
    freeCompilerArgs.addAll("-Xannotation-default-target=param-property")
  }
}

@CacheableTask
abstract class WriteJsonTask : DefaultTask() {
  private companion object {
    private val mapper = JsonMapper()
  }

  @get:Input
  abstract val url: Property<String>

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  @TaskAction
  fun run() {
    val json = URI.create(url.get()).toURL().readText()
    val formattedJson = mapper.let { mapper ->
      mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
    }
    outputFile.get().asFile.writeText(formattedJson)
    logger.lifecycle("Written ${outputFile.get()} from ${url.get()}")
  }
}

@CacheableTask
abstract class ReadProductionVersionTask : DefaultTask() {
  private companion object {
    private val mapper = JsonMapper()
  }

  @get:Input
  abstract val url: Property<String>

  @TaskAction
  fun run() {
    val productionUrl = url.get().replace("-dev".toRegex(), "")
      .replace("dev.".toRegex(), "")
      .replace("/v3/api-docs".toRegex(), "/info")
    val json = URI.create(productionUrl).toURL().readText()
    val version = mapper.readTree(json).at("/build/version").asString()
    println(version)
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
    get() = "$projectDir/openapi-specs/$name-api-docs.json"
  val output: String
    get() = name
}

val packagePrefix = "uk.gov.justice.digital.hmpps.prisonertonomisupdate"
val models = listOf(
  ModelConfiguration(
    name = "activities",
    packageName = "activities",
    testPackageName = "activities",
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
    name = "court-scheduler",
    packageName = "courtscheduler",
    url = "https://court-appearance-scheduler-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
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
    testPackageName = "incentives",
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
    testPackageName = "nonassociations",
    url = "https://non-associations-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "LegacyNonAssociation,LegacyNonAssociationOtherPrisonerDetails,NonAssociation",
  ),
  ModelConfiguration(
    name = "locations",
    packageName = "locations",
    testPackageName = "locations",
    url = "https://locations-inside-prison-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "Capacity,CellCertificateLocationDto,ChangeHistory,LegacyLocation,Location,NonResidentialUsageDto,PatchNonResidentialLocationRequest,PendingChangeDto,ServiceUsingLocationDto,TransactionDetail,TransactionHistory",
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
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
  withType<KtLintFormatTask> {
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
}
val separateTestPackages = mutableListOf<String>()
models.forEachIndexed { i, model ->
  tasks.register<GenerateTask>(model.toBuildModelTaskName()) {
    val buildDirectory: DirectoryProperty = project.layout.buildDirectory
    group = "Generate model from API JSON definition"
    description = "Generate model from API JSON definition for ${model.name}"
    generatorName.set("kotlin")
    library.set("jvm-spring-webclient")
    skipValidateSpec.set(true)
    inputSpec.set(model.input)
    outputDir.set(buildDirectory.dir("generated/${model.output}").get().asFile.path)
    modelPackage.set("$packagePrefix.${model.packageName}.model")
    apiPackage.set("$packagePrefix.${model.packageName}.api")
    configOptions.set(configValues)
    KotlinPath("$projectDir/openapi-generator-ignore-${model.name}")
      .takeIf { p -> p.exists() }?.apply { ignoreFileOverride.set(this.pathString) }
      ?: globalProperties.set(mapOf("models" to model.models))
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
    mustRunAfter(model.toWriteJsonTaskName())
    // GenerateTask is not safe to run in parallel so need to ensure that only one runs at once
    if (i != 0) mustRunAfter(models[i - 1].toBuildModelTaskName())
  }
  tasks.register<WriteJsonTask>(model.toWriteJsonTaskName()) {
    val buildDirectory: DirectoryProperty = project.layout.buildDirectory
    group = "Write JSON"
    description = "Write JSON for ${model.name}"
    url.set(model.url)
    outputFile.set(buildDirectory.file(model.input))
    // ensure that the write task happens every time
    outputs.upToDateWhen { false }
  }
  tasks.register<ReadProductionVersionTask>(model.toReadProductionVersionTaskName()) {
    group = "Read current production version"
    description = "Read current production version for ${model.name}"
    url.set(model.url)
  }
  if (model.testPackageName != null) {
    separateTestPackages.add(model.testPackageName)
    val test by testing.suites.existing(JvmTestSuite::class)
    val task = tasks.register<Test>(model.toTestTaskName()) {
      testClassesDirs = files(test.map { it.sources.output.classesDirs })
      classpath = files(test.map { it.sources.runtimeClasspath })
      group = "Run tests"
      description = "Run tests for ${model.name}"
      shouldRunAfter("test")
      useJUnitPlatform()
      filter {
        includeTestsMatching("$packagePrefix.${model.testPackageName}.*")
      }
      maxHeapSize = "1024m"
    }
    tasks.check { dependsOn(task) }
  }
}

tasks.test {
  filter {
    separateTestPackages.forEach {
      excludeTestsMatching("$packagePrefix.$it.*")
    }
  }
  maxHeapSize = "1024m"
}

val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
  "useSpringBoot3" to "true",
)

kotlin {
  val buildDirectory: DirectoryProperty = project.layout.buildDirectory
  sourceSets["main"].apply {
    models.map { it.output }.forEach { generatedProject ->
      kotlin.srcDir(buildDirectory.dir("generated/$generatedProject/src/main/kotlin").get().toString())
    }
  }
}

ktlint {
  val buildDirectory: DirectoryProperty = project.layout.buildDirectory
  models.map { it.output }.forEach { generatedProject ->
    filter {
      exclude {
        it.file.path.contains(buildDirectory.dir("generated/$generatedProject/src/main/").get().toString())
      }
    }
  }
}
