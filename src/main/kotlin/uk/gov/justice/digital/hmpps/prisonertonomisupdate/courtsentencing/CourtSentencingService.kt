package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourtAppearanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCourtCaseRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class CourtSentencingService(
  private val courtSentencingApiService: CourtSentencingApiService,
  private val nomisApiService: NomisApiService,
  private val courtCaseMappingService: CourtCaseMappingService,
  private val courtSentencingRetryQueueService: CourtSentencingRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  enum class EntityType(val displayName: String) {
    COURT_CASE("court-case"),
  }

  suspend fun createCourtCase(createEvent: CourtCaseCreatedEvent) {
    val courtCaseId = createEvent.additionalInformation.id
    val offenderNo: String = createEvent.additionalInformation.offenderNo
    val telemetryMap = mutableMapOf(
      "courtCaseId" to courtCaseId,
      "offenderNo" to offenderNo,
    )
    synchronise {
      name = EntityType.COURT_CASE.displayName
      telemetryClient = this@CourtSentencingService.telemetryClient
      retryQueueService = courtSentencingRetryQueueService
      eventTelemetry = telemetryMap

      checkMappingDoesNotExist {
        courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(courtCaseId)
      }

      transform {
        val courtCase = courtSentencingApiService.getCourtCase(courtCaseId)
        telemetryMap["offenderNo"] = offenderNo
        val nomisResponse =
          nomisApiService.createCourtCase(offenderNo, courtCase.toNomisCourtCase())

        CourtCaseMappingDto(
          nomisCourtCaseId = nomisResponse.id,
          dpsCourtCaseId = courtCaseId,
          // todo court appearance mappings
        )
      }
      saveMapping { courtCaseMappingService.createMapping(it) }
    }
  }

  suspend fun createRetry(message: CreateMappingRetryMessage<CourtCaseMappingDto>) =
    courtCaseMappingService.createMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "court-case-create-mapping-retry-success",
        message.telemetryAttributes,
        null,
      )
    }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (baseMapping.entityName) {
      EntityType.COURT_CASE.displayName -> createRetry(message.fromJson())
      else -> throw IllegalArgumentException("Unknown entity type: ${baseMapping.entityName}")
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)

  data class AdditionalInformation(
    val id: String,
    val offenderNo: String,
    val source: String,
  )

  data class CourtCaseCreatedEvent(
    val additionalInformation: AdditionalInformation,
  )
}

fun CourtCase.toNomisCourtCase(): CreateCourtCaseRequest = CreateCourtCaseRequest(
  startDate = this.latestAppearance.appearanceDate,
  courtId = this.latestAppearance.courtCode,
  // TODO fix LocalDateTime as string,
  courtAppearance = CourtAppearanceRequest(
    eventDateTime = LocalDateTime.of(
      this.latestAppearance.appearanceDate,
      LocalTime.MIDNIGHT,
    ).toString(),
    courtId = this.latestAppearance.courtCode,
    courtEventType = "TBC",
    courtEventChargesToUpdate = listOf(),
    courtEventChargesToCreate = listOf(),
  ),
  legalCaseType = "TBC",
  status = "TBC",
)
