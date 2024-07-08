package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.Charge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceAllMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeBatchUpdateMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeNomisIdDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourtAppearanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCourtCaseRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ExistingOffenderChargeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.OffenderChargeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.AdditionalInformation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.createMapping
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

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  enum class EntityType(val displayName: String) {
    COURT_CASE("court-case"),
    COURT_APPEARANCE("court-appearance"),
    COURT_CHARGE("court-appearance"),
  }

  suspend fun createCourtCase(createEvent: CourtCaseCreatedEvent) {
    val courtCaseId = createEvent.additionalInformation.courtCaseId
    val offenderNo: String = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to courtCaseId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(createEvent.additionalInformation)) {
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
    } else {
      telemetryMap["reason"] = "Court case created in NOMIS"
      telemetryClient.trackEvent(
        "court-case-create-ignored",
        telemetryMap,
        null,
      )
    }
  }

  private fun isDpsCreated(additionalInformation: AdditionalInformation) =
    additionalInformation.source != CreatingSystem.NOMIS.name

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

  suspend fun updateCourtAppearance(createEvent: CourtAppearanceCreatedEvent) {
    val courtCaseId = createEvent.additionalInformation.courtCaseId
    val courtAppearanceId = createEvent.additionalInformation.id
    val offenderNo: String = createEvent.additionalInformation.offenderNo
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to courtCaseId,
      "dpsCourtAppearanceId" to courtAppearanceId,
      "offenderNo" to offenderNo,
    )

    runCatching {
      val courtCaseMapping =
        courtCaseMappingService.getMappingGivenCourtCaseId(courtCaseId).also {
          telemetryMap["nomisCourtCaseId"] = it.nomisCourtCaseId.toString()
        }
      val courtAppearanceMapping =
        courtCaseMappingService.getMappingGivenCourtAppearanceId(courtAppearanceId)
          .also {
            telemetryMap["nomisCourtAppearanceId"] = it.nomisCourtAppearanceId.toString()
          }

      val dpsCourtAppearance = courtSentencingApiService.getCourtAppearance(courtAppearanceId)

      val courtEventChargesToUpdate: MutableList<Pair<Charge, Long>> = mutableListOf()
      val courtEventChargesToCreate: MutableList<Charge> = mutableListOf()
      dpsCourtAppearance.charges.forEach { charge ->
        courtCaseMappingService.getMappingGivenCourtChargeIdOrNull(charge.chargeUuid.toString())?.let { mapping ->
          courtEventChargesToUpdate.add(Pair(charge, mapping.nomisCourtChargeId))
        } ?: let {
          courtEventChargesToCreate.add(charge)
        }
      }

      val nomisResponse = nomisApiService.updateCourtAppearance(
        offenderNo = offenderNo,
        nomisCourtCaseId = courtCaseMapping.nomisCourtCaseId,
        nomisCourtAppearanceId = courtAppearanceMapping.nomisCourtAppearanceId,
        request = dpsCourtAppearance.toNomisCourtAppearance(
          courtEventChargesToCreate = courtEventChargesToCreate.map { it.toNomisCourtCharge() },
          courtEventChargesToUpdate = courtEventChargesToUpdate.map { it.first.toExistingNomisCourtCharge(it.second) },
        ),
      )

      CourtChargeBatchUpdateMappingDto(
        courtChargesToCreate = courtEventChargesToCreate.zip(nomisResponse.createdCourtEventChargesIds) { charge, nomisChargeResponseDto ->
          CourtChargeMappingDto(
            nomisCourtChargeId = nomisChargeResponseDto.offenderChargeId,
            dpsCourtChargeId = charge.chargeUuid.toString(),
          )
        },
        courtChargesToDelete = nomisResponse.deletedOffenderChargesIds.map {
          CourtChargeNomisIdDto(
            nomisCourtChargeId = it.offenderChargeId,
          )
        },
      ).takeIf { it.hasAnyMappingsToUpdate() }?.run {
        telemetryMap["newCourtChargeMappings"] = this.courtChargesToCreate.map { "dpsCourtChargeId: ${it.dpsCourtChargeId}, nomisCourtChargeId: ${it.nomisCourtChargeId}" }.toString()
        telemetryMap["deletedCourtChargeMappings"] = this.courtChargesToDelete.map { "nomisCourtChargeId: ${it.nomisCourtChargeId}" }.toString()
        createMapping(
          mapping = this,
          telemetryClient = telemetryClient,
          retryQueueService = courtSentencingRetryQueueService,
          eventTelemetry = telemetryMap,
          name = EntityType.COURT_CHARGE.displayName,
          postMapping = { courtCaseMappingService.createChargeBatchUpdateMapping(it) },
          log = log,
        )
      }

      telemetryClient.trackEvent(
        "court-appearance-updated-success",
        telemetryMap,
      )
    }.onFailure { e ->
      telemetryClient.trackEvent("court-appearance-updated-failed", telemetryMap, null)
      throw e
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
    val courtCaseId: String,
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
    val personReference: PersonReferenceList,
  )

  data class CourtAppearanceCreatedEvent(
    val additionalInformation: CourtAppearanceAdditionalInformation,
  )
}

fun CourtCase.toNomisCourtCase(): CreateCourtCaseRequest = CreateCourtCaseRequest(
  startDate = this.latestAppearance.appearanceDate,
  courtId = this.latestAppearance.courtCode,
  courtAppearance = CourtAppearanceRequest(
    eventDateTime = LocalDateTime.of(
      this.latestAppearance.appearanceDate,
      LocalTime.MIDNIGHT,
    ).toString(),
    courtId = this.latestAppearance.courtCode,
    // TODO these are MOV_RSN on NOMIS - defaulting to Court Appearance until DPS provide a mapping
    courtEventType = "CA",
    courtEventChargesToUpdate = listOf(),
    courtEventChargesToCreate = this.latestAppearance.charges.mapIndexed { index, dpsCharge ->
      dpsCharge.toNomisCourtCharge()
    },
  ),
  // LEG_CASE_TYP on NOMIS - defaulting to Adult as suggested in the Sentencing document
  legalCaseType = "A",
  // CASE_STS on NOMIS - no decision from DPS yet - defaulting to Active
  status = "A",
)

fun CourtAppearance.toNomisCourtAppearance(
  courtEventChargesToCreate: List<OffenderChargeRequest>,
  courtEventChargesToUpdate: List<ExistingOffenderChargeRequest>,
): CourtAppearanceRequest {
  return CourtAppearanceRequest(
    // Just date without time recorded against the DPS appearance
    eventDateTime = LocalDateTime.of(
      this.appearanceDate,
      LocalTime.MIDNIGHT,
    ).toString(),
    // TODO these are MOV_RSN on NOMIS - defaulting to Court Appearance until DPS provide a mapping
    courtEventType = "CA",
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
  // TODO determine if this comes from DPS or is it determined
  offencesCount = 1,
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
  offencesCount = 1,
)

private fun CourtChargeBatchUpdateMappingDto.hasAnyMappingsToUpdate(): Boolean =
  this.courtChargesToCreate.isNotEmpty() || this.courtChargesToDelete.isNotEmpty()
