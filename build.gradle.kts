import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "6.0.8"
  kotlin("plugin.spring") version "2.0.21"
  id("org.openapi.generator") version "7.9.0"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.0.8")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.data:spring-data-commons:3.3.5")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.1.0")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.6.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.9.0")
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.43.0")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.0.8")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.23") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.25")

  testImplementation("org.wiremock:wiremock-standalone:3.9.2")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.testcontainers:localstack:1.20.3")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.777")
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
      "buildContactPersonApiModel",
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
      "buildContactPersonApiModel",
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
      "buildContactPersonApiModel",
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
  globalProperties.set(
    mapOf(
      "models" to "Activity,ActivityLite,ActivityEligibility,EventTier,EventOrganiser,DeallocationReason,EarliestReleaseDate,PlannedDeallocation,PlannedSuspension,AttendanceHistory,AttendanceReason,ActivityScheduleLite,InternalLocation,EligibilityRule,ActivityCategory,ActivityMinimumEducationLevel,BookingCount,PrisonPayBand,Attendance,ActivityPay,ActivitySchedule,ActivityScheduleInstance,ActivityScheduleSlot,Allocation,AllocationReconciliationResponse,AppointmentInstance,AttendanceReconciliationResponse,AttendanceSync,ScheduledInstance,Slot,Suspension",
      "modelDocs" to "false",
      "modelTests" to "false",
    ),
  )
}

tasks.register("buildAdjudicationApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/adjudications-api-docs.json")
  outputDir.set("$buildDirectory/generated/adjudications")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.api")
  configOptions.set(configValues)
  globalProperties.set(
    mapOf(
      "models" to "HearingDto,HearingOutcomeDto,OffenceRuleDto,OffenceRuleDetailsDto,IncidentRoleDto,IncidentStatementDto,OutcomeDto,OutcomeHistoryDto,PunishmentDto,CombinedOutcomeDto,RehabilitativeActivityDto,PunishmentScheduleDto,ReportedAdjudicationDto,IncidentDetailsDto,OffenceDto,PunishmentCommentDto,ReportedWitnessDto,DisIssueHistoryDto,ReportedAdjudicationResponse,ReportedDamageDto,ReportedEvidenceDto,Type",
      "modelDocs" to "false",
      "modelTests" to "false",
    ),
  )
}

tasks.register("buildNonAssociationApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/non-associations-api-docs.json")
  outputDir.set("$buildDirectory/generated/nonassociations")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.api")
  configOptions.set(configValues)
  globalProperties.set(
    mapOf(
      "models" to "LegacyNonAssociation,LegacyNonAssociationOtherPrisonerDetails,NonAssociation",
      "modelDocs" to "false",
      "modelTests" to "false",
    ),
  )
}

tasks.register("buildLocationApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/locations-api-docs.json")
  outputDir.set("$buildDirectory/generated/locations")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.api")
  configOptions.set(configValues)
  globalProperties.set(
    mapOf(
      "models" to "ChangeHistory,LegacyLocation,Certification,Capacity,NonResidentialUsageDto",
      "modelDocs" to "false",
      "modelTests" to "false",
    ),
  )
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
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/sentencing-adjustments-api-docs.json")
  outputDir.set("$buildDirectory/generated/sentencingadjustments")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.api")
  configOptions.set(configValues)
  globalProperties.set(
    mapOf(
      "models" to "RemandDto,LegacyAdjustment,UnlawfullyAtLargeDto,SpecialRemissionDto,AdjustmentDto,TaggedBailDto,SpecialRemissionDtoAdditionalDaysAwardedDto,LawfullyAtLargeDto,AdditionalDaysAwardedDto,",
      "modelDocs" to "false",
      "modelTests" to "false",
    ),
  )
}

tasks.register("buildCourtSentencingApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/court-sentencing-api-docs.json")
  outputDir.set("$buildDirectory/generated/courtsentencing")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.api")
  configOptions.set(configValues)
  globalProperties.set(
    mapOf(
      "models" to "CourtCase,Charge,ChargeOutcome,SentenceType,Sentence,CourtAppearance,PeriodLength,NextCourtAppearance,CourtAppearanceOutcome",
      "modelDocs" to "false",
      "modelTests" to "false",
    ),
  )
}

tasks.register("buildAlertsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/alerts-api-docs.json")
  outputDir.set("$buildDirectory/generated/alerts")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.api")
  configOptions.set(configValues)
  globalProperties.set(
    mapOf(
      "models" to "Alert,AlertCode,AlertType,AlertCodeSummary",
      "modelDocs" to "false",
      "modelTests" to "false",
    ),
  )
}

tasks.register("buildCsipApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/csip-api-docs.json")
  outputDir.set("$buildDirectory/generated/csip")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.api")
  configOptions.set(configValues)
  globalProperties.set(
    mapOf(
      "models" to "Attendee,ContributoryFactor,CsipRecord,DecisionAndActions,IdentifiedNeed,Interview,Investigation,Plan,ReferenceData,Referral,Review,SaferCustodyScreeningOutcome",
      "modelDocs" to "false",
      "modelTests" to "false",
    ),
  )
}

tasks.register("buildCaseNoteApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/casenotes-api-docs.json")
  outputDir.set("$buildDirectory/generated/casenotes")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.api")
  configOptions.set(configValues)
  globalProperties.set(
    mapOf(
      "models" to "CaseNote,CaseNoteAmendment",
      "modelDocs" to "false",
      "modelTests" to "false",
    ),
  )
}

tasks.register("buildPrisonPersonApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/prison-person-api-docs.json")
  outputDir.set("$buildDirectory/generated/prisonperson")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.api")
  configOptions.set(configValues)
  globalProperties.set(
    mapOf(
      "models" to "PhysicalAttributesSyncDto",
      "modelDocs" to "false",
      "modelTests" to "false",
    ),
  )
}

tasks.register("buildContactPersonApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/contact-person-api-docs.json")
  outputDir.set("$buildDirectory/generated/contactperson")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.api")
  configOptions.set(configValues)
  globalProperties.set(
    mapOf(
      "models" to "Contact",
      "modelDocs" to "false",
      "modelTests" to "false",
    ),
  )
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
  "contactperson",
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
