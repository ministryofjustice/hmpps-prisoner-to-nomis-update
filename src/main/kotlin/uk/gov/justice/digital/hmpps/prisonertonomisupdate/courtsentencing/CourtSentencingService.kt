package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.Charge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceAllMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourtAppearanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCourtCaseRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ExistingOffenderChargeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.OffenderChargeRequest
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
    COURT_APPEARANCE("court-appearance"),
  }

  suspend fun createCourtCase(createEvent: CourtCaseCreatedEvent) {
    val courtCaseId = createEvent.additionalInformation.id
    val offenderNo: String = createEvent.additionalInformation.offenderNo
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to courtCaseId,
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

        val nomisCourtAppearanceResponse = nomisResponse.courtAppearanceIds.first()
        CourtCaseAllMappingDto(
          nomisCourtCaseId = nomisResponse.id,
          dpsCourtCaseId = courtCaseId,
          // expecting a court case with 1 court appearance - separate event for subsequent appearances
          courtAppearances = listOf(
            CourtAppearanceMappingDto(
              dpsCourtAppearanceId = courtCase.latestAppearance.appearanceUuid.toString(),
              nomisCourtAppearanceId = nomisCourtAppearanceResponse.id,
              nomisNextCourtAppearanceId = nomisCourtAppearanceResponse.nextCourtAppearanceId,
            ),
          ),
          courtCharges = courtCase.latestAppearance.charges.mapIndexed { index, dpsCharge ->
            CourtChargeMappingDto(
              nomisCourtChargeId = nomisCourtAppearanceResponse.courtEventChargesIds[index].offenderChargeId,
              dpsCourtChargeId = dpsCharge.chargeUuid.toString(),
            )
          },
        )
      }
      saveMapping { courtCaseMappingService.createMapping(it) }
    }
  }

  suspend fun createCourtAppearance(createEvent: CourtAppearanceCreatedEvent) {
    val courtCaseId = createEvent.additionalInformation.courtCaseId
    val courtAppearanceId = createEvent.additionalInformation.id
    val offenderNo: String = createEvent.additionalInformation.offenderNo
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to courtCaseId,
      "dpsCourtAppearanceId" to courtAppearanceId,
      "offenderNo" to offenderNo,
    )
    synchronise {
      name = EntityType.COURT_APPEARANCE.displayName
      telemetryClient = this@CourtSentencingService.telemetryClient
      retryQueueService = courtSentencingRetryQueueService
      eventTelemetry = telemetryMap

      checkMappingDoesNotExist {
        courtCaseMappingService.getMappingGivenCourtAppearanceIdOrNull(courtAppearanceId)
      }

      transform {
        val courtCaseMapping = courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId = courtCaseId)
          ?: throw IllegalStateException(
            "Attempt to create a court appearance on a dps court case without a nomis mapping. Dps court case id: $courtCaseId not found for DPS court appearance $courtAppearanceId",
          )

        val courtAppearance = courtSentencingApiService.getCourtAppearance(courtAppearanceId)

        val courtEventChargesToUpdate: MutableList<Pair<Charge, Long>> = mutableListOf()
        val courtEventChargesToCreate: MutableList<Charge> = mutableListOf()
        courtAppearance.charges.forEach { charge ->
          courtCaseMappingService.getMappingGivenCourtChargeIdOrNull(charge.chargeUuid.toString())?.let { mapping ->
            courtEventChargesToUpdate.add(Pair(charge, mapping.nomisCourtChargeId))
          } ?: let {
            courtEventChargesToCreate.add(charge)
          }
        }

        val nomisCourtAppearanceResponse =
          nomisApiService.createCourtAppearance(
            offenderNo,
            courtCaseMapping.nomisCourtCaseId,
            courtAppearance.toNomisCourtAppearance(
              courtEventChargesToCreate = courtEventChargesToCreate.map { it.toNomisCourtCharge() },
              courtEventChargesToUpdate = courtEventChargesToUpdate.map { it.first.toExistingNomisCourtCharge(it.second) },
            ),
          )

        CourtAppearanceAllMappingDto(
          dpsCourtAppearanceId = courtAppearanceId,
          nomisCourtAppearanceId = nomisCourtAppearanceResponse.id,
          nomisNextCourtAppearanceId = nomisCourtAppearanceResponse.nextCourtAppearanceId,
          courtCharges = courtEventChargesToCreate.zip(nomisCourtAppearanceResponse.courtEventChargesIds) { charge, nomisChargeResponseDto ->
            CourtChargeMappingDto(
              nomisCourtChargeId = nomisChargeResponseDto.offenderChargeId,
              dpsCourtChargeId = charge.chargeUuid.toString(),
            )
          },
        ).also {
          telemetryMap["courtCharges"] = courtEventChargesToCreate.toString()
          telemetryMap["nomisCourtCaseId"] = courtCaseMapping.nomisCourtCaseId.toString()
        }
      }
      saveMapping { courtCaseMappingService.createAppearanceMapping(it) }
    }
  }

  suspend fun createRetry(message: CreateMappingRetryMessage<CourtCaseAllMappingDto>) =
    courtCaseMappingService.createMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "court-case-create-mapping-retry-success",
        message.telemetryAttributes,
        null,
      )
    }

  suspend fun createAppearanceRetry(message: CreateMappingRetryMessage<CourtAppearanceAllMappingDto>) =
    courtCaseMappingService.createAppearanceMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "court-appearance-create-mapping-retry-success",
        message.telemetryAttributes,
        null,
      )
    }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (baseMapping.entityName) {
      EntityType.COURT_CASE.displayName -> createRetry(message.fromJson())
      EntityType.COURT_APPEARANCE.displayName -> createAppearanceRetry(message.fromJson())
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

  data class CourtAppearanceAdditionalInformation(
    val id: String,
    val offenderNo: String,
    val source: String,
    val courtCaseId: String,
  )

  data class CourtCaseCreatedEvent(
    val additionalInformation: AdditionalInformation,
  )

  data class CourtAppearanceCreatedEvent(
    val additionalInformation: CourtAppearanceAdditionalInformation,
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
    courtEventChargesToCreate = this.latestAppearance.charges.mapIndexed { index, dpsCharge ->
      dpsCharge.toNomisCourtCharge()
    },
  ),
  legalCaseType = "TBC",
  status = "TBC",
)

fun CourtAppearance.toNomisCourtAppearance(
  courtEventChargesToCreate: List<OffenderChargeRequest>,
  courtEventChargesToUpdate: List<ExistingOffenderChargeRequest>,
): CourtAppearanceRequest {
  return CourtAppearanceRequest(
    // expecting time to be added to the remand and sentencing model at some point
    eventDateTime = LocalDateTime.of(
      this.appearanceDate,
      LocalTime.MIDNIGHT,
    ).toString(),
    courtEventType = "TBC",
    courtId = this.courtCode,
    outcomeReasonCode = this.outcome,
    nextEventDateTime = nextCourtAppearance?.let {
      LocalDateTime.of(
        nextCourtAppearance.appearanceDate,
        LocalTime.MIDNIGHT,
      ).toString()
    },
    courtEventChargesToUpdate = courtEventChargesToUpdate,
    courtEventChargesToCreate = courtEventChargesToCreate,
    nextCourtId = nextCourtAppearance?.courtCode,
  )
}

fun Charge.toNomisCourtCharge(): OffenderChargeRequest = OffenderChargeRequest(
  offenceCode = this.offenceCode,
  offenceDate = this.offenceStartDate,
  offenceEndDate = this.offenceEndDate,
// TODO dps has text that 'mainly' matches nomis but there are also non-matching values on T3
  resultCode1 = this.outcome,
// TODO do dps provide this?
  mostSeriousFlag = false,
)

fun Charge.toExistingNomisCourtCharge(nomisId: Long): ExistingOffenderChargeRequest = ExistingOffenderChargeRequest(
  offenderChargeId = nomisId,
  offenceCode = this.offenceCode,
  offenceDate = this.offenceStartDate,
  offenceEndDate = this.offenceEndDate,
// TODO dps has text that 'mainly' matches nomis but there are also non-matching values on T3
  resultCode1 = this.outcome,
// TODO do dps provide this?
  mostSeriousFlag = false,
)
