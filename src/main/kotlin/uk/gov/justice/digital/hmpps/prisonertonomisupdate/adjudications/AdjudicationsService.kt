package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingOutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeHistoryDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentDto.Type
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentScheduleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedDamageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedEvidenceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.LocationsMappingService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationDeleteMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationHearingMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationPunishmentBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationPunishmentBatchUpdateMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationPunishmentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationPunishmentNomisIdDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AdjudicationChargeId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ChargeToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAdjudicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateHearingRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateHearingResultRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.EvidenceToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.EvidenceToUpdateOrAdd
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ExistingHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.HearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.HearingResultAwardRequest.SanctionStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.HearingResultAwardRequest.SanctionType.FORFEIT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RepairToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RepairToUpdateOrAdd
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UnquashHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateEvidenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateHearingRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateRepairsRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.createMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class AdjudicationsService(
  private val telemetryClient: TelemetryClient,
  private val adjudicationRetryQueueService: AdjudicationsRetryQueueService,
  private val adjudicationMappingService: AdjudicationsMappingService,
  private val hearingMappingService: HearingsMappingService,
  private val adjudicationsApiService: AdjudicationsApiService,
  private val punishmentsMappingService: PunishmentsMappingService,
  private val locationsMappingService: LocationsMappingService,
  private val nomisApiService: NomisApiService,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  enum class EntityType(val displayName: String) {
    HEARING("hearing"),
    ADJUDICATION("adjudication"),
    PUNISHMENT("punishment"),
    PUNISHMENT_UPDATE("punishment-update"),
    PUNISHMENT_DELETE("punishment-delete"),
    PUNISHMENT_UNQUASH("punishment-unquash"),
  }

  suspend fun createAdjudication(createEvent: AdjudicationCreatedEvent): Long? {
    val chargeNumber = createEvent.additionalInformation.chargeNumber
    val prisonId: String = createEvent.additionalInformation.prisonId
    val telemetryMap = mutableMapOf(
      "chargeNumber" to chargeNumber,
      "prisonId" to prisonId,
    )
    var createdNomisAdjudicationNumber: Long? = null
    synchronise {
      name = EntityType.ADJUDICATION.displayName
      telemetryClient = this@AdjudicationsService.telemetryClient
      retryQueueService = adjudicationRetryQueueService
      eventTelemetry = telemetryMap

      checkMappingDoesNotExist {
        adjudicationMappingService.getMappingGivenChargeNumberOrNull(createEvent.additionalInformation.chargeNumber)
      }

      transform {
        val adjudication = adjudicationsApiService.getCharge(chargeNumber, prisonId)
        val offenderNo = adjudication.reportedAdjudication.prisonerNumber
        telemetryMap["offenderNo"] = offenderNo

        // TODO future change Just call mapping service to get nomisLocationId once satisfied location picked up correctly
        val locationId = getNomisLocationByDpsId(
          originalNomisLocationId = adjudication.reportedAdjudication.incidentDetails.locationId!!,
          dpsLocationId = adjudication.reportedAdjudication.incidentDetails.locationUuid,
        )
        val nomisAdjudicationResponse =
          nomisApiService.createAdjudication(offenderNo, adjudication.toNomisAdjudication(locationId))

        createdNomisAdjudicationNumber = nomisAdjudicationResponse.adjudicationNumber
        AdjudicationMappingDto(
          adjudicationNumber = nomisAdjudicationResponse.adjudicationNumber,
          chargeSequence = nomisAdjudicationResponse.charges.first().chargeSequence,
          chargeNumber = createEvent.additionalInformation.chargeNumber,
        )
      }
      saveMapping { adjudicationMappingService.createMapping(it) }
    }

    return createdNomisAdjudicationNumber
  }

  suspend fun repairAdjudication(
    prisonId: String,
    offenderNo: String,
    chargeNumber: String,
  ): Long {
    val adjudication = adjudicationsApiService.getCharge(chargeNumber, prisonId)

    adjudicationMappingService.deleteMappingsForAdjudication(adjudication.toDeleteMappingDto())

    val adjudicationNumber = createAdjudication(
      AdjudicationCreatedEvent(
        AdjudicationAdditionalInformation(
          chargeNumber = chargeNumber,
          prisonId = prisonId,
          prisonerNumber = offenderNo,
        ),
      ),
    )
    adjudication.reportedAdjudication.hearings.forEach {
      createHearing(
        HearingEvent(
          HearingAdditionalInformation(
            chargeNumber = chargeNumber,
            prisonId = prisonId,
            prisonerNumber = offenderNo,
            hearingId = it.id.toString(),
          ),
        ),
      )
    }
    adjudication.reportedAdjudication.outcomes.forEach {
      upsertOutcome(
        OutcomeEvent(
          OutcomeAdditionalInformation(
            chargeNumber = chargeNumber,
            prisonId = prisonId,
            prisonerNumber = offenderNo,
            hearingId = it.hearing?.id.toString(),
          ),
        ),
      )
    }
    createPunishments(
      chargeNumber = chargeNumber,
      prisonId = prisonId,
      prisonerNumber = offenderNo,
      hearingDate = adjudication.reportedAdjudication.hearings.latestHearingDate(),
    )

    return adjudicationNumber!!
  }

  suspend fun createRetry(message: CreateMappingRetryMessage<AdjudicationMappingDto>) {
    adjudicationMappingService.createMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "adjudication-create-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  suspend fun createHearingRetry(message: CreateMappingRetryMessage<AdjudicationHearingMappingDto>) {
    hearingMappingService.createMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "hearing-create-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  suspend fun createPunishmentRetry(message: CreateMappingRetryMessage<AdjudicationPunishmentBatchMappingDto>) {
    punishmentsMappingService.createMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "punishment-create-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  suspend fun createPunishmentUpdateRetry(message: CreateMappingRetryMessage<AdjudicationPunishmentBatchUpdateMappingDto>) {
    punishmentsMappingService.updateMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "punishment-update-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  suspend fun createPunishmentDeleteRetry(message: CreateMappingRetryMessage<AdjudicationPunishmentBatchUpdateMappingDto>) {
    punishmentsMappingService.updateMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "punishment-delete-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  suspend fun createPunishmentUnquashRetry(message: CreateMappingRetryMessage<AdjudicationPunishmentBatchUpdateMappingDto>) {
    punishmentsMappingService.updateMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "punishment-unquash-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (baseMapping.entityName) {
      EntityType.ADJUDICATION.displayName -> createRetry(message.fromJson())
      EntityType.HEARING.displayName -> createHearingRetry(message.fromJson())
      EntityType.PUNISHMENT.displayName -> createPunishmentRetry(message.fromJson())
      EntityType.PUNISHMENT_UPDATE.displayName -> createPunishmentUpdateRetry(message.fromJson())
      EntityType.PUNISHMENT_DELETE.displayName -> createPunishmentDeleteRetry(message.fromJson())
      EntityType.PUNISHMENT_UNQUASH.displayName -> createPunishmentUnquashRetry(message.fromJson())
      else -> throw IllegalArgumentException("Unknown entity type: ${baseMapping.entityName}")
    }
  }

  suspend fun updateAdjudicationDamages(damagesUpdateEvent: AdjudicationDamagesUpdateEvent) {
    val chargeNumber = damagesUpdateEvent.additionalInformation.chargeNumber
    val prisonId: String = damagesUpdateEvent.additionalInformation.prisonId
    val offenderNo: String = damagesUpdateEvent.additionalInformation.prisonerNumber
    val telemetryMap = mutableMapOf(
      "chargeNumber" to chargeNumber,
      "prisonId" to prisonId,
      "offenderNo" to offenderNo,
    )

    runCatching {
      val mapping =
        adjudicationMappingService.getMappingGivenChargeNumber(chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
            telemetryMap["chargeSequence"] = it.chargeSequence.toString()
          }

      val dpsAdjudication = adjudicationsApiService.getCharge(chargeNumber, prisonId)
      nomisApiService.updateAdjudicationRepairs(
        mapping.adjudicationNumber,
        UpdateRepairsRequest(repairs = dpsAdjudication.reportedAdjudication.damages.map { it.toNomisRepairForUpdate() }),
      ).also {
        telemetryMap["repairCount"] = it.repairs.size.toString()
      }

      telemetryClient.trackEvent(
        "adjudication-damages-updated-success",
        telemetryMap,
      )
    }.onFailure { e ->
      telemetryClient.trackEvent("adjudication-damages-updated-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun updateAdjudicationEvidence(evidenceUpdateEvent: AdjudicationEvidenceUpdateEvent) {
    val chargeNumber = evidenceUpdateEvent.additionalInformation.chargeNumber
    val prisonId: String = evidenceUpdateEvent.additionalInformation.prisonId
    val offenderNo: String = evidenceUpdateEvent.additionalInformation.prisonerNumber
    val telemetryMap = mutableMapOf(
      "chargeNumber" to chargeNumber,
      "prisonId" to prisonId,
      "offenderNo" to offenderNo,
    )

    runCatching {
      val mapping =
        adjudicationMappingService.getMappingGivenChargeNumber(chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
            telemetryMap["chargeSequence"] = it.chargeSequence.toString()
          }

      val dpsAdjudication = adjudicationsApiService.getCharge(chargeNumber, prisonId)
      nomisApiService.updateAdjudicationEvidence(
        mapping.adjudicationNumber,
        UpdateEvidenceRequest(evidence = dpsAdjudication.reportedAdjudication.evidence.map { it.toNomisUpdateEvidence() }),
      ).also {
        telemetryMap["evidenceCount"] = it.evidence.size.toString()
      }

      telemetryClient.trackEvent(
        "adjudication-evidence-updated-success",
        telemetryMap,
      )
    }.onFailure { e ->
      telemetryClient.trackEvent("adjudication-evidence-updated-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun createHearing(
    chargeNumber: String,
    prisonId: String,
    offenderNo: String,
    hearingId: String,
  ) = createHearing(
    HearingEvent(
      HearingAdditionalInformation(
        chargeNumber = chargeNumber,
        prisonId = prisonId,
        prisonerNumber = offenderNo,
        hearingId = hearingId,
      ),
    ),
  )

  suspend fun createHearing(createEvent: HearingEvent) {
    val chargeNumber = createEvent.additionalInformation.chargeNumber
    val prisonId: String = createEvent.additionalInformation.prisonId
    val prisonerNumber: String = createEvent.additionalInformation.prisonerNumber
    val dpsHearingId: String = createEvent.additionalInformation.hearingId
    val telemetryMap = mutableMapOf(
      "chargeNumber" to chargeNumber,
      "prisonId" to prisonId,
      "offenderNo" to prisonerNumber,
      "dpsHearingId" to dpsHearingId,
    )
    synchronise {
      name = EntityType.HEARING.displayName
      telemetryClient = this@AdjudicationsService.telemetryClient
      retryQueueService = adjudicationRetryQueueService
      eventTelemetry = telemetryMap

      checkMappingDoesNotExist {
        hearingMappingService.getMappingGivenDpsHearingIdOrNull(createEvent.additionalInformation.hearingId)
      }

      transform {
        val adjudicationNumber =
          adjudicationMappingService.getMappingGivenChargeNumber(chargeNumber).adjudicationNumber
            .also {
              telemetryMap["adjudicationNumber"] = it.toString()
            }
        log.info("Hearing: $dpsHearingId with charge number: $chargeNumber has adjudication number: $adjudicationNumber")
        val hearing = adjudicationsApiService.getCharge(
          chargeNumber,
          prisonId,
        ).reportedAdjudication.hearings.lastOrNull { it.id.toString() == dpsHearingId } ?: throw IllegalStateException(
          "Hearing $dpsHearingId not found for DPS adjudication with charge no $chargeNumber",
        )
        // TODO future change Just call mapping service to get nomisLocationId once satisfied location picked up correctly
        val nomisLocationId = getNomisLocationByDpsId(originalNomisLocationId = hearing.locationId!!, dpsLocationId = hearing.locationUuid)
        val nomisAdjudicationResponse = nomisApiService.createHearing(adjudicationNumber, hearing.toNomisCreateHearing(nomisLocationId))

        telemetryMap["nomisHearingId"] = nomisAdjudicationResponse.hearingId.toString()

        AdjudicationHearingMappingDto(
          dpsHearingId = createEvent.additionalInformation.hearingId,
          nomisHearingId = nomisAdjudicationResponse.hearingId,
        )
      }
      saveMapping { hearingMappingService.createMapping(it) }
    }
  }

  suspend fun updateHearing(updateEvent: HearingEvent) {
    val eventData: HearingAdditionalInformation = updateEvent.additionalInformation
    val telemetryMap = updateEvent.additionalInformation.toTelemetryMap()

    runCatching {
      val adjudicationNumber =
        adjudicationMappingService.getMappingGivenChargeNumber(eventData.chargeNumber).adjudicationNumber
          .also {
            telemetryMap["adjudicationNumber"] = it.toString()
          }
      val nomisHearingId =
        hearingMappingService.getMappingGivenDpsHearingId(eventData.hearingId).nomisHearingId
          .also { telemetryMap["nomisHearingId"] = it.toString() }

      adjudicationsApiService.getCharge(
        eventData.chargeNumber,
        eventData.prisonId,
      ).reportedAdjudication.hearings.lastOrNull { it.id.toString() == eventData.hearingId }?.let {
        // TODO future change Just call mapping service to get nomisLocationId once satisfied location picked up correctly
        val hearingLocationId = getNomisLocationByDpsId(it.locationId!!, it.locationUuid)
        nomisApiService.updateHearing(adjudicationNumber, nomisHearingId, it.toNomisUpdateHearing(hearingLocationId))
        telemetryClient.trackEvent("hearing-updated-success", telemetryMap, null)
      } ?: throw IllegalStateException(
        "Hearing ${eventData.hearingId} not found for DPS adjudication with charge no ${eventData.chargeNumber}",
      )
    }.onFailure { e ->
      telemetryClient.trackEvent("hearing-updated-failed", telemetryMap, null)
      throw e
    }
  }

  // TODO future change Remove logging and use value only from mapping service once satisfied location picked up correctly
  suspend fun getNomisLocationByDpsId(originalNomisLocationId: Long, dpsLocationId: UUID): Long = runCatching {
    locationsMappingService.getMappingGivenDpsId(dpsLocationId.toString()).nomisLocationId
  }.getOrElse {
    log.debug("Failed to get Location mapping for Dps Id {}, using Nomis Id {} due to {}", dpsLocationId, originalNomisLocationId, it.message)
    originalNomisLocationId
  }

  suspend fun deleteHearing(deleteEvent: HearingEvent) {
    val eventData: HearingAdditionalInformation = deleteEvent.additionalInformation
    val telemetryMap = deleteEvent.additionalInformation.toTelemetryMap()

    runCatching {
      val adjudicationNumber =
        adjudicationMappingService.getMappingGivenChargeNumber(eventData.chargeNumber).adjudicationNumber
          .also {
            telemetryMap["adjudicationNumber"] = it.toString()
          }
      val nomisHearingId =
        hearingMappingService.getMappingGivenDpsHearingId(eventData.hearingId).nomisHearingId
          .also { telemetryMap["nomisHearingId"] = it.toString() }

      nomisApiService.deleteHearing(adjudicationNumber, nomisHearingId).also {
        hearingMappingService.deleteMappingGivenDpsHearingId(eventData.hearingId)
      }
    }.onSuccess {
      telemetryClient.trackEvent("hearing-deleted-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("hearing-deleted-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun upsertOutcome(createEvent: OutcomeEvent) {
    createEvent.additionalInformation.hearingId?.let {
      createOrUpdateHearingCompleted(
        createEvent.toHearingEvent(),
      )
    }
      ?: let { createOrUpdateReferral(createEvent.toReferralEvent()) }
  }

  suspend fun createOrUpdateHearingCompleted(createEvent: HearingEvent) {
    val event = createEvent.additionalInformation
    val telemetryMap = event.toTelemetryMap()

    val charge = adjudicationsApiService.getCharge(
      event.chargeNumber,
      event.prisonId,
    )

    runCatching {
      val outcome = charge.reportedAdjudication.outcomes.lastOrNull { it.hearing?.id.toString() == event.hearingId }
        ?: throw IllegalStateException(
          "Outcome not found for Hearing ${event.hearingId} in DPS adjudication with charge no ${event.chargeNumber}",
        )

      val referralOutcome = outcome.getReferralOutcome()

      val adjudicationMapping =
        adjudicationMappingService.getMappingGivenChargeNumber(event.chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
            telemetryMap["chargeSequence"] = it.chargeSequence.toString()
          }

      val hearingMapping =
        hearingMappingService.getMappingGivenDpsHearingIdOrNull(dpsHearingId = event.hearingId)
          ?.also { telemetryMap["nomisHearingId"] = it.nomisHearingId.toString() }
          ?: throw IllegalStateException(
            "Hearing mapping for dps hearing id: ${event.hearingId} not found for DPS adjudication with charge no ${event.chargeNumber}",
          )

      val nomisRequest =
        referralOutcome?.let { outcome.toNomisCreateHearingResultForReferralOutcome() }
          ?: let { outcome.toNomisCreateHearingResult() }
      nomisApiService.upsertHearingResult(
        adjudicationNumber = adjudicationMapping.adjudicationNumber,
        hearingId = hearingMapping.nomisHearingId,
        chargeSequence = adjudicationMapping.chargeSequence,
        request = nomisRequest,
      ).also {
        telemetryMap["findingCode"] = nomisRequest.findingCode
        telemetryMap["plea"] = nomisRequest.pleaFindingCode
      }
    }.onSuccess {
      telemetryClient.trackEvent("hearing-result-upserted-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("hearing-result-upserted-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun deleteOutcome(deleteEvent: OutcomeEvent) {
    deleteEvent.additionalInformation.hearingId?.let {
      deleteHearingCompleted(
        deleteEvent.toHearingEvent(),
      )
    }
      ?: let { deleteReferral(deleteEvent.toReferralEvent()) }
  }

  suspend fun deleteHearingCompleted(
    chargeNumber: String,
    prisonId: String,
    prisonerNumber: String,
    hearingId: String,
  ) {
    deleteHearingCompleted(
      HearingEvent(
        HearingAdditionalInformation(
          chargeNumber = chargeNumber,
          prisonId = prisonId,
          prisonerNumber = prisonerNumber,
          hearingId = hearingId,
        ),
      ),
    )
  }

  suspend fun deleteHearingCompleted(deleteEvent: HearingEvent) {
    val eventData: HearingAdditionalInformation = deleteEvent.additionalInformation
    val telemetryMap = deleteEvent.additionalInformation.toTelemetryMap()

    runCatching {
      val adjudicationMapping =
        adjudicationMappingService.getMappingGivenChargeNumber(eventData.chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
          }
      val hearingMapping =
        hearingMappingService.getMappingGivenDpsHearingIdOrNull(eventData.hearingId)
          ?.also { telemetryMap["nomisHearingId"] = it.nomisHearingId.toString() }

      if (hearingMapping != null) {
        val outcome = adjudicationsApiService.getCharge(
          eventData.chargeNumber,
          eventData.prisonId,
        ).reportedAdjudication.outcomes.lastOrNull()

        // If no adjudication outcome exists - delete on NOMIS
        // if outcome exists - update NOMIS with current state
        outcome?.getOutcome()?.let {
          nomisApiService.upsertHearingResult(
            adjudicationNumber = adjudicationMapping.adjudicationNumber,
            hearingId = hearingMapping.nomisHearingId,
            chargeSequence = adjudicationMapping.chargeSequence,
            request = outcome.toNomisCreateHearingResult(),
          )
        } ?: let {
          nomisApiService.deleteHearingResult(
            adjudicationNumber = adjudicationMapping.adjudicationNumber,
            hearingId = hearingMapping.nomisHearingId,
            chargeSequence = adjudicationMapping.chargeSequence,
          ).also {
            telemetryMap["punishmentsDeletedCount"] = it.awardsDeleted.size.toString()

            AdjudicationPunishmentBatchUpdateMappingDto(
              punishmentsToCreate = emptyList(),
              punishmentsToDelete = it.awardsDeleted.map {
                AdjudicationPunishmentNomisIdDto(
                  nomisBookingId = it.bookingId,
                  nomisSanctionSequence = it.sanctionSequence,
                )
              },
            ).takeIf { it.hasAnyMappingsToUpdate() }?.run {
              // if the removal of the outcome has had the side effect of removing punishments - we delete the punishment mappings
              createMapping(
                mapping = this,
                telemetryClient = telemetryClient,
                retryQueueService = adjudicationRetryQueueService,
                eventTelemetry = telemetryMap,
                name = EntityType.PUNISHMENT_DELETE.displayName,
                postMapping = { punishmentsMappingService.updateMapping(it) },
                log = log,
              )
            }
          }
        }
        telemetryClient.trackEvent("hearing-result-deleted-success", telemetryMap, null)
      } else {
        telemetryClient.trackEvent("hearing-result-deleted-skipped", telemetryMap, null)
      }
    }.onFailure { e ->
      telemetryClient.trackEvent("hearing-result-deleted-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun deleteReferral(deleteEvent: ReferralEvent) {
    val eventData: ReferralAdditionalInformation = deleteEvent.additionalInformation
    val telemetryMap = deleteEvent.additionalInformation.toTelemetryMap()

    runCatching {
      val adjudicationMapping =
        adjudicationMappingService.getMappingGivenChargeNumber(eventData.chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
          }

      val outcome = adjudicationsApiService.getCharge(
        eventData.chargeNumber,
        eventData.prisonId,
      ).reportedAdjudication.outcomes.lastOrNull()

      // If no adjudication outcome exists - delete on NOMIS
      // if outcome exists - update NOMIS with current state
      outcome?.getOutcome()?.let {
        nomisApiService.upsertReferral(
          adjudicationMapping.adjudicationNumber,
          adjudicationMapping.chargeSequence,
          outcome.toNomisCreateReferral(),
        )
      } ?: let {
        nomisApiService.deleteReferralResult(
          adjudicationMapping.adjudicationNumber,
          adjudicationMapping.chargeSequence,
        )
      }
    }.onSuccess {
      telemetryClient.trackEvent("hearing-result-deleted-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("hearing-result-deleted-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun createPunishments(punishmentEvent: PunishmentEvent) = createPunishments(
    chargeNumber = punishmentEvent.additionalInformation.chargeNumber,
    prisonId = punishmentEvent.additionalInformation.prisonId,
    prisonerNumber = punishmentEvent.additionalInformation.prisonerNumber,
  )
  suspend fun createPunishments(
    chargeNumber: String,
    prisonId: String,
    prisonerNumber: String,
    hearingDate: LocalDate? = null,
  ) {
    val telemetryMap = mutableMapOf(
      "chargeNumber" to chargeNumber,
      "prisonId" to prisonId,
      "offenderNo" to prisonerNumber,
    )

    synchronise {
      name = EntityType.PUNISHMENT.displayName
      telemetryClient = this@AdjudicationsService.telemetryClient
      retryQueueService = adjudicationRetryQueueService
      eventTelemetry = telemetryMap

      transform {
        val mapping =
          adjudicationMappingService.getMappingGivenChargeNumber(chargeNumber)
            .also {
              telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
              telemetryMap["chargeSequence"] = it.chargeSequence.toString()
            }

        val adjudicationNumber = mapping.adjudicationNumber
        val chargeSequence = mapping.chargeSequence

        val punishments = adjudicationsApiService.getCharge(
          chargeNumber,
          prisonId,
        ).reportedAdjudication.punishments

        val nomisAwardsResponse =
          nomisApiService.createAdjudicationAwards(
            adjudicationNumber,
            chargeSequence,
            CreateHearingResultAwardRequest(awards = punishments.map { it.toNomisAward(hearingDate) }),
          )
            .also { telemetryMap["punishmentsCount"] = it.awardsCreated.size.toString() }

        AdjudicationPunishmentBatchMappingDto(
          punishments = punishments.zip(nomisAwardsResponse.awardsCreated) { punishment, awardResponse ->
            AdjudicationPunishmentMappingDto(
              dpsPunishmentId = punishment.id.toString(),
              nomisBookingId = awardResponse.bookingId,
              nomisSanctionSequence = awardResponse.sanctionSequence,
            )
          },
        )
      }
      saveMapping { punishmentsMappingService.createMapping(it) }
    }
  }

  suspend fun updatePunishments(prisonId: String, offenderNo: String, chargeNumber: String) = updatePunishments(
    PunishmentEvent(
      PunishmentsAdditionalInformation(
        chargeNumber = chargeNumber,
        prisonId = prisonId,
        prisonerNumber = offenderNo,
      ),
    ),
  )

  suspend fun upsertOutcome(prisonId: String, offenderNo: String, chargeNumber: String, hearingId: String) = upsertOutcome(
    OutcomeEvent(
      OutcomeAdditionalInformation(
        chargeNumber = chargeNumber,
        prisonId = prisonId,
        hearingId = hearingId,
        prisonerNumber = offenderNo,
      ),
    ),
  )

  suspend fun updatePunishments(punishmentEvent: PunishmentEvent) {
    val eventInfo = punishmentEvent.additionalInformation
    val telemetryMap = eventInfo.toTelemetryMap()

    runCatching {
      val adjudicationMapping =
        adjudicationMappingService.getMappingGivenChargeNumber(eventInfo.chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
            telemetryMap["chargeSequence"] = it.chargeSequence.toString()
          }

      val adjudicationNumber = adjudicationMapping.adjudicationNumber
      val chargeSequence = adjudicationMapping.chargeSequence

      val punishments = adjudicationsApiService.getCharge(
        eventInfo.chargeNumber,
        eventInfo.prisonId,
      ).reportedAdjudication.punishments

      val punishmentsToUpdate: List<Pair<PunishmentDto, Int>> = punishments.mapNotNull { punishment ->
        punishmentsMappingService.getMapping(punishment.id.toString())?.let {
          punishment to it.nomisSanctionSequence
        }
      }
      val punishmentsToCreate =
        punishments.filter { punishment -> punishmentsToUpdate.none { it.first.id == punishment.id } }

      val nomisAwardsResponse =
        nomisApiService.updateAdjudicationAwards(
          adjudicationNumber,
          chargeSequence,
          UpdateHearingResultAwardRequest(
            awardsToUpdate = punishmentsToUpdate.map {
              ExistingHearingResultAwardRequest(
                award = it.first.toNomisAward(),
                sanctionSequence = it.second,
              )
            },
            awardsToCreate = punishmentsToCreate.map { it.toNomisAward() },
          ),
        )
          .also {
            telemetryMap["punishmentsCreatedCount"] = punishmentsToCreate.size.toString()
            telemetryMap["punishmentsUpdatedCount"] = punishmentsToUpdate.size.toString()
            telemetryMap["punishmentsDeletedCount"] = it.awardsDeleted.size.toString()
          }

      val mapping = AdjudicationPunishmentBatchUpdateMappingDto(
        punishmentsToCreate = punishmentsToCreate.zip(nomisAwardsResponse.awardsCreated) { punishment, awardResponse ->
          AdjudicationPunishmentMappingDto(
            dpsPunishmentId = punishment.id.toString(),
            nomisBookingId = awardResponse.bookingId,
            nomisSanctionSequence = awardResponse.sanctionSequence,
          )
        },
        punishmentsToDelete = nomisAwardsResponse.awardsDeleted.map {
          AdjudicationPunishmentNomisIdDto(
            nomisBookingId = it.bookingId,
            nomisSanctionSequence = it.sanctionSequence,
          )
        },
      )
      createMapping(
        mapping = mapping,
        telemetryClient = telemetryClient,
        retryQueueService = adjudicationRetryQueueService,
        eventTelemetry = telemetryMap,
        name = EntityType.PUNISHMENT_UPDATE.displayName,
        postMapping = { punishmentsMappingService.updateMapping(it) },
        log = log,
      )
    }.onSuccess {
      telemetryClient.trackEvent("punishment-update-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("punishment-update-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun unquashPunishments(punishmentEvent: PunishmentEvent) {
    val eventInfo = punishmentEvent.additionalInformation
    val telemetryMap = eventInfo.toTelemetryMap()

    runCatching {
      val adjudicationMapping =
        adjudicationMappingService.getMappingGivenChargeNumber(eventInfo.chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
            telemetryMap["chargeSequence"] = it.chargeSequence.toString()
          }

      val adjudicationNumber = adjudicationMapping.adjudicationNumber
      val chargeSequence = adjudicationMapping.chargeSequence

      val adjudication = adjudicationsApiService.getCharge(
        eventInfo.chargeNumber,
        eventInfo.prisonId,
      ).reportedAdjudication
      val punishments = adjudication.punishments
      val finalOutcome = adjudication.outcomes.last()

      val punishmentsToUpdate: List<Pair<PunishmentDto, Int>> = punishments.mapNotNull { punishment ->
        punishmentsMappingService.getMapping(punishment.id.toString())?.let {
          punishment to it.nomisSanctionSequence
        }
      }
      val punishmentsToCreate =
        punishments.filter { punishment -> punishmentsToUpdate.none { it.first.id == punishment.id } }

      val nomisAwardsResponse =
        nomisApiService.unquashAdjudicationAwards(
          adjudicationNumber,
          chargeSequence,
          request = UnquashHearingResultAwardRequest(
            findingCode = finalOutcome.toFindingCode(),
            awards =
            UpdateHearingResultAwardRequest(
              awardsToUpdate = punishmentsToUpdate.map {
                ExistingHearingResultAwardRequest(
                  award = it.first.toNomisAward(),
                  sanctionSequence = it.second,
                )
              },
              awardsToCreate = punishmentsToCreate.map { it.toNomisAward() },
            ),
          ),
        )
          .also {
            telemetryMap["punishmentsCreatedCount"] = punishmentsToCreate.size.toString()
            telemetryMap["punishmentsUpdatedCount"] = punishmentsToUpdate.size.toString()
            telemetryMap["punishmentsDeletedCount"] = it.awardsDeleted.size.toString()
          }

      AdjudicationPunishmentBatchUpdateMappingDto(
        punishmentsToCreate = punishmentsToCreate.zip(nomisAwardsResponse.awardsCreated) { punishment, awardResponse ->
          AdjudicationPunishmentMappingDto(
            dpsPunishmentId = punishment.id.toString(),
            nomisBookingId = awardResponse.bookingId,
            nomisSanctionSequence = awardResponse.sanctionSequence,
          )
        },
        punishmentsToDelete = nomisAwardsResponse.awardsDeleted.map {
          AdjudicationPunishmentNomisIdDto(
            nomisBookingId = it.bookingId,
            nomisSanctionSequence = it.sanctionSequence,
          )
        },
      ).takeIf { it.hasAnyMappingsToUpdate() }?.run {
        createMapping(
          mapping = this,
          telemetryClient = telemetryClient,
          retryQueueService = adjudicationRetryQueueService,
          eventTelemetry = telemetryMap,
          name = EntityType.PUNISHMENT_UNQUASH.displayName,
          postMapping = { punishmentsMappingService.updateMapping(it) },
          log = log,
        )
      }
    }.onSuccess {
      telemetryClient.trackEvent("punishment-unquash-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("punishment-unquash-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun deletePunishments(punishmentEvent: PunishmentEvent) {
    val eventInfo = punishmentEvent.additionalInformation
    val telemetryMap = eventInfo.toTelemetryMap()

    runCatching {
      val adjudicationMapping =
        adjudicationMappingService.getMappingGivenChargeNumber(eventInfo.chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
            telemetryMap["chargeSequence"] = it.chargeSequence.toString()
          }

      val adjudicationNumber = adjudicationMapping.adjudicationNumber
      val chargeSequence = adjudicationMapping.chargeSequence

      val nomisAwardsDeleted = adjudicationsApiService.getCharge(
        eventInfo.chargeNumber,
        eventInfo.prisonId,
      ).reportedAdjudication.punishments.let {
        if (it.isEmpty()) {
          nomisApiService.deleteAdjudicationAwards(
            adjudicationNumber,
            chargeSequence,
          )
        } else {
          throw IllegalStateException(
            "Punishments exist in DPS for offenderNo ${eventInfo.prisonerNumber} charge no ${eventInfo.chargeNumber}. Ignoring delete punishments event.",
          )
        }
      }.also { telemetryMap["punishmentsDeletedCount"] = it.awardsDeleted.size.toString() }

      AdjudicationPunishmentBatchUpdateMappingDto(
        punishmentsToCreate = emptyList(),
        punishmentsToDelete = nomisAwardsDeleted.awardsDeleted.map {
          AdjudicationPunishmentNomisIdDto(
            nomisBookingId = it.bookingId,
            nomisSanctionSequence = it.sanctionSequence,
          )
        },
      ).takeIf { it.hasAnyMappingsToUpdate() }?.run {
        createMapping(
          mapping = this,
          telemetryClient = telemetryClient,
          retryQueueService = adjudicationRetryQueueService,
          eventTelemetry = telemetryMap,
          name = EntityType.PUNISHMENT_DELETE.displayName,
          postMapping = { punishmentsMappingService.updateMapping(this) },
          log = log,
        )
      }
    }.onSuccess {
      telemetryClient.trackEvent("punishment-delete-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("punishment-delete-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun quashPunishments(punishmentEvent: PunishmentEvent) {
    val chargeNumber = punishmentEvent.additionalInformation.chargeNumber
    val prisonId: String = punishmentEvent.additionalInformation.prisonId
    val prisonerNumber: String = punishmentEvent.additionalInformation.prisonerNumber
    val telemetryMap = mutableMapOf(
      "chargeNumber" to chargeNumber,
      "prisonId" to prisonId,
      "offenderNo" to prisonerNumber,
    )

    runCatching {
      val adjudicationMapping =
        adjudicationMappingService.getMappingGivenChargeNumber(chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
            telemetryMap["chargeSequence"] = it.chargeSequence.toString()
          }

      val adjudicationNumber = adjudicationMapping.adjudicationNumber
      val chargeSequence = adjudicationMapping.chargeSequence

      val finalOutcome = adjudicationsApiService.getCharge(
        chargeNumber,
        prisonId,
      ).reportedAdjudication.outcomes.lastOrNull()

      if (finalOutcome?.outcome?.outcome?.code == OutcomeDto.Code.QUASHED) {
        nomisApiService.quashAdjudicationAwards(adjudicationNumber, chargeSequence)
      } else {
        // if we find this is common and requires no further investigation we could eventually
        // consider making this just a warning and ignoring this event. For now, be cautious and
        // alert on DLQ events for this
        throw AdjudicationOutcomeInWrongState(finalOutcome?.outcome?.outcome?.code)
      }
    }.onSuccess {
      telemetryClient.trackEvent("punishment-quash-success", telemetryMap, null)
    }.onFailure { e ->
      when (e) {
        is AdjudicationOutcomeInWrongState -> {
          telemetryClient.trackEvent(
            "punishment-quash-failed",
            telemetryMap + ("reason" to "Outcome is ${e.outcome}"),
            null,
          )
        }

        else -> {
          telemetryClient.trackEvent("punishment-quash-failed", telemetryMap, null)
        }
      }
      throw e
    }
  }

  // referral without a hearing
  suspend fun createOrUpdateReferral(createEvent: ReferralEvent) {
    val event = createEvent.additionalInformation
    val telemetryMap = event.toTelemetryMap()

    runCatching {
      val charge = adjudicationsApiService.getCharge(
        event.chargeNumber,
        event.prisonId,
      )
      val outcome = charge.reportedAdjudication.outcomes.lastOrNull()
        ?: throw IllegalStateException(
          "Referral not found in DPS adjudication with charge no ${event.chargeNumber}",
        )

      val adjudicationMapping =
        adjudicationMappingService.getMappingGivenChargeNumber(event.chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
            telemetryMap["chargeSequence"] = it.chargeSequence.toString()
          }

      val nomisRequest =
        outcome.getReferralOutcome()?.let { outcome.toNomisCreateReferralForReferralOutcome() }
          ?: let { outcome.toNomisCreateReferral() }
      nomisApiService.upsertReferral(
        adjudicationNumber = adjudicationMapping.adjudicationNumber,
        chargeSequence = adjudicationMapping.chargeSequence,
        request = nomisRequest,
      ).also {
        telemetryMap["findingCode"] = nomisRequest.findingCode
        telemetryMap["plea"] = nomisRequest.pleaFindingCode
      }
    }.onSuccess {
      telemetryClient.trackEvent("adjudication-referral-upserted-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("adjudication-referral-upserted-failed", telemetryMap, null)
      throw e
    }
  }

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)

  private suspend fun PunishmentDto.toNomisAward(hearingDate: LocalDate? = null) = HearingResultAwardRequest(
    sanctionType = this.type.toNomisSanctionType(),
    sanctionStatus = this.toNomisSanctionStatus(),
    effectiveDate = this.schedule.startDate ?: this.schedule.suspendedUntil ?: hearingDate ?: LocalDate.now(),
    sanctionDays = this.schedule.takeIf { it.measurement == PunishmentScheduleDto.Measurement.DAYS }?.duration ?: 0,
    compensationAmount = this.damagesOwedAmount?.toBigDecimal() ?: stoppagePercentage?.toBigDecimal(),
    commentText = this.toComment(),
    consecutiveCharge = this.consecutiveChargeNumber?.let { chargeNumber ->
      adjudicationMappingService.getMappingGivenChargeNumber(chargeNumber)
        .let { AdjudicationChargeId(it.adjudicationNumber, it.chargeSequence) }
    },
  )
}

private fun List<HearingDto>.latestHearingDate(): LocalDate? = this.map { it.dateTimeOfHearing }.maxOfOrNull { it.toLocalDate() }

private fun ReportedAdjudicationResponse.toDeleteMappingDto() = AdjudicationDeleteMappingDto(
  dpsChargeNumber = this.reportedAdjudication.chargeNumber,
  dpsHearingIds = this.reportedAdjudication.hearings.map { "${it.id}" },
  dpsPunishmentIds = this.reportedAdjudication.punishments.map { "${it.id}" },
)

private fun AdjudicationPunishmentBatchUpdateMappingDto.hasAnyMappingsToUpdate(): Boolean = this.punishmentsToCreate.isNotEmpty() || this.punishmentsToDelete.isNotEmpty()

private fun OutcomeEvent.toHearingEvent(): HearingEvent = HearingEvent(
  HearingAdditionalInformation(
    chargeNumber = this.additionalInformation.chargeNumber,
    prisonerNumber = this.additionalInformation.prisonerNumber,
    prisonId = this.additionalInformation.prisonId,
    hearingId = this.additionalInformation.hearingId!!,
  ),
)

private fun OutcomeEvent.toReferralEvent(): ReferralEvent = ReferralEvent(
  ReferralAdditionalInformation(
    chargeNumber = this.additionalInformation.chargeNumber,
    prisonerNumber = this.additionalInformation.prisonerNumber,
    prisonId = this.additionalInformation.prisonId,
  ),
)

private fun PunishmentDto.toComment(): String = // copy of existing logic from prison-api
  when (this.type) {
    Type.PRIVILEGE ->
      when (this.privilegeType) {
        PunishmentDto.PrivilegeType.OTHER -> "Added by DPS: Loss of ${this.otherPrivilege}"
        else -> "Added by DPS: Loss of ${this.privilegeType}"
      }

    Type.DAMAGES_OWED -> "Added by DPS: OTHER - Damages owed £${String.format("%.2f", this.damagesOwedAmount!!)}"
    else -> "Added by DPS"
  }

private fun PunishmentDto.toNomisSanctionStatus(): SanctionStatus {
  // copy of existing logic from prison-api
  val prospectiveDays = type == Type.PROSPECTIVE_DAYS

  return when (this.schedule.startDate) {
    null -> when (this.schedule.suspendedUntil) {
      null -> if (prospectiveDays) SanctionStatus.PROSPECTIVE else SanctionStatus.IMMEDIATE
      else -> if (prospectiveDays) SanctionStatus.SUSP_PROSP else SanctionStatus.SUSPENDED
    }

    else -> SanctionStatus.IMMEDIATE
  }
}

private fun Type.toNomisSanctionType(): HearingResultAwardRequest.SanctionType = when (this) {
  Type.PRIVILEGE -> FORFEIT
  Type.EARNINGS -> HearingResultAwardRequest.SanctionType.STOP_PCT
  Type.CONFINEMENT -> HearingResultAwardRequest.SanctionType.CC
  Type.REMOVAL_ACTIVITY -> HearingResultAwardRequest.SanctionType.REMACT
  Type.EXCLUSION_WORK -> HearingResultAwardRequest.SanctionType.EXTRA_WORK // yes, this mapping is correct
  Type.EXTRA_WORK -> HearingResultAwardRequest.SanctionType.EXTW
  Type.REMOVAL_WING -> HearingResultAwardRequest.SanctionType.REMWIN
  Type.ADDITIONAL_DAYS -> HearingResultAwardRequest.SanctionType.ADA
  Type.PROSPECTIVE_DAYS -> HearingResultAwardRequest.SanctionType.ADA
  Type.CAUTION -> HearingResultAwardRequest.SanctionType.CAUTION
  Type.DAMAGES_OWED -> HearingResultAwardRequest.SanctionType.OTHER
  Type.PAYBACK -> HearingResultAwardRequest.SanctionType.PP
}

private fun HearingDto.toNomisUpdateHearing(nomisInternalLocationId: Long): UpdateHearingRequest = UpdateHearingRequest(
  hearingType = this.oicHearingType.name,
  hearingDate = this.dateTimeOfHearing.toLocalDate(),
  hearingTime = this.dateTimeOfHearing.toLocalTimeAtMinute().toString(),
  internalLocationId = nomisInternalLocationId,
)

// If OutcomeDto exists use code, else use code from hearingOutcomeDto
private fun OutcomeHistoryDto.toNomisCreateHearingResult(): CreateHearingResultRequest {
  val separateOutcome: OutcomeDto? = this.outcome?.outcome
  val hearing = this.hearing!!
  val findingCode = separateOutcome?.code?.name ?: hearing.outcome!!.code.name

  return CreateHearingResultRequest(
    pleaFindingCode = hearing.outcome!!.plea?.let { toNomisPleaCode(it) } ?: HearingOutcomeDto.Plea.NOT_ASKED.name,
    findingCode = toNomisFindingCode(findingCode),
    adjudicatorUsername = getAdjudicatorUsernameForInternalHearingOnly(
      hearing.oicHearingType.name,
      hearing.outcome!!.adjudicator,
    ),
  )
}

private fun OutcomeHistoryDto.toFindingCode(): String {
  val separateOutcome: OutcomeDto? = this.outcome?.outcome
  val hearing = this.hearing!!
  val findingCode = separateOutcome?.code?.name ?: hearing.outcome!!.code.name

  return toNomisFindingCode(findingCode)
}

private fun OutcomeHistoryDto.toNomisCreateHearingResultForReferralOutcome(): CreateHearingResultRequest {
  val hearing = this.hearing!!
  val findingCode = this.outcome!!.referralOutcome!!.code.name
  return CreateHearingResultRequest(
    pleaFindingCode = "NOT_ASKED",
    findingCode = toNomisFindingCode(findingCode),
    adjudicatorUsername = getAdjudicatorUsernameForInternalHearingOnly(
      hearing.oicHearingType.name,
      hearing.outcome!!.adjudicator,
    ),
  )
}

private fun OutcomeHistoryDto.toNomisCreateReferral(): CreateHearingResultRequest {
  val findingCode = this.outcome!!.outcome.code.name
  return CreateHearingResultRequest(
    pleaFindingCode = "NOT_ASKED",
    findingCode = toNomisFindingCode(findingCode),
  )
}

private fun OutcomeHistoryDto.toNomisCreateReferralForReferralOutcome(): CreateHearingResultRequest {
  val findingCode = this.outcome!!.referralOutcome!!.code.name
  return CreateHearingResultRequest(
    pleaFindingCode = "NOT_ASKED",
    findingCode = toNomisFindingCode(findingCode),
  )
}

private fun OutcomeHistoryDto.getReferralOutcome(): String? = this.outcome?.referralOutcome?.code?.name

private fun OutcomeHistoryDto.getOutcome(): String? = this.outcome?.outcome?.code?.name

private fun toNomisFindingCode(code: String) = when (code) {
  OutcomeDto.Code.REFER_POLICE.name -> "REF_POLICE"
  OutcomeDto.Code.REFER_INAD.name -> "ADJOURNED" // TODO from John/Tim - to confirm
  OutcomeDto.Code.REFER_GOV.name -> "ADJOURNED" // TODO from John/Tim - to confirm
  OutcomeDto.Code.NOT_PROCEED.name -> "NOT_PROCEED"
  OutcomeDto.Code.DISMISSED.name -> "D"
  OutcomeDto.Code.PROSECUTION.name -> "PROSECUTED"
  OutcomeDto.Code.CHARGE_PROVED.name -> "PROVED"
  OutcomeDto.Code.QUASHED.name -> "QUASHED"
  HearingOutcomeDto.Code.ADJOURN.name -> "ADJOURNED"
  else -> throw InvalidFindingCodeException("DPS Outcome code $code does not map to a NOMIS finding code")
}

private fun toNomisPleaCode(plea: HearingOutcomeDto.Plea) = when (plea) {
  HearingOutcomeDto.Plea.UNFIT -> "UNFIT"
  HearingOutcomeDto.Plea.ABSTAIN -> "REFUSED"
  HearingOutcomeDto.Plea.GUILTY -> "GUILTY"
  HearingOutcomeDto.Plea.NOT_GUILTY -> "NOT_GUILTY"
  HearingOutcomeDto.Plea.NOT_ASKED -> "NOT_ASKED"
}

private fun getAdjudicatorUsernameForInternalHearingOnly(hearingType: String, adjudicator: String) = when (hearingType) {
  HearingDto.OicHearingType.INAD_ADULT.name, HearingDto.OicHearingType.INAD_YOI.name -> null
  else -> adjudicator.takeIf { it.isNotBlank() }
}

private fun HearingAdditionalInformation.toTelemetryMap(): MutableMap<String, String> = mutableMapOf(
  "chargeNumber" to this.chargeNumber,
  "prisonId" to this.prisonId,
  "offenderNo" to this.prisonerNumber,
  "dpsHearingId" to this.hearingId,
)

private fun ReferralAdditionalInformation.toTelemetryMap(): MutableMap<String, String> = mutableMapOf(
  "chargeNumber" to this.chargeNumber,
  "prisonId" to this.prisonId,
  "offenderNo" to this.prisonerNumber,
)

private fun PunishmentsAdditionalInformation.toTelemetryMap(): MutableMap<String, String> = mutableMapOf(
  "chargeNumber" to this.chargeNumber,
  "prisonId" to this.prisonId,
  "offenderNo" to this.prisonerNumber,
)

internal fun ReportedAdjudicationResponse.toNomisAdjudication(nomisInternalLocationId: Long) = CreateAdjudicationRequest(
  incident = IncidentToCreate(
    reportingStaffUsername = reportedAdjudication.createdByUserId,
    incidentDate = reportedAdjudication.incidentDetails.dateTimeOfDiscovery.toLocalDate(),
    incidentTime = reportedAdjudication.incidentDetails.dateTimeOfDiscovery.toLocalTimeAtMinute().toString(),
    reportedDate = reportedAdjudication.createdDateTime.toLocalDate(),
    reportedTime = reportedAdjudication.createdDateTime.toLocalTimeAtMinute().toString(),
    internalLocationId = nomisInternalLocationId,
    details = reportedAdjudication.incidentStatement.statement,
    prisonId = reportedAdjudication.originatingAgencyId,
    prisonerVictimsOffenderNumbers = reportedAdjudication.offenceDetails.victimPrisonersNumber
      ?.takeUnless { isVictimAlsoTheSuspect() }
      ?.let { listOf(it) }
      ?: emptyList(),
    // Not stored in DPS so can not be synchronised
    staffWitnessesUsernames = emptyList(),
    staffVictimsUsernames = reportedAdjudication.offenceDetails.victimStaffUsername?.let { listOf(it) } ?: emptyList(),
    repairs = reportedAdjudication.damages.map { it.toNomisRepairForCreate() },
  ),
  charges = listOf(
    ChargeToCreate(
      offenceCode = reportedAdjudication.getOffenceCode(),
    ),
  ),
  evidence = reportedAdjudication.evidence.map { it.toNomisCreateEvidence() },
)

private fun ReportedAdjudicationResponse.isVictimAlsoTheSuspect() = reportedAdjudication.offenceDetails.victimPrisonersNumber == reportedAdjudication.prisonerNumber

private fun ReportedAdjudicationDto.getOffenceCode() = if (this.didInciteOtherPrisoner()) {
  // for migrated offences this nomisCode will be absent so use paragraphNumber instead
  this.offenceDetails.offenceRule.withOthersNomisCode ?: this.offenceDetails.offenceRule.paragraphNumber
} else {
  this.offenceDetails.offenceRule.nomisCode ?: this.offenceDetails.offenceRule.paragraphNumber
}

private fun ReportedAdjudicationDto.didInciteOtherPrisoner() = this.incidentRole.roleCode != null

private fun ReportedDamageDto.toNomisRepairForCreate() = RepairToCreate(
  typeCode = this.code.toNomisCreateEnum(),
  comment = this.details,
)

private fun ReportedDamageDto.toNomisRepairForUpdate() = RepairToUpdateOrAdd(
  typeCode = this.code.toNomisUpdateEnum(),
  comment = this.details,
)

private fun ReportedDamageDto.Code.toNomisUpdateEnum(): RepairToUpdateOrAdd.TypeCode = when (this) {
  ReportedDamageDto.Code.ELECTRICAL_REPAIR -> RepairToUpdateOrAdd.TypeCode.ELEC
  ReportedDamageDto.Code.PLUMBING_REPAIR -> RepairToUpdateOrAdd.TypeCode.PLUM
  ReportedDamageDto.Code.FURNITURE_OR_FABRIC_REPAIR -> RepairToUpdateOrAdd.TypeCode.FABR
  ReportedDamageDto.Code.LOCK_REPAIR -> RepairToUpdateOrAdd.TypeCode.LOCK
  ReportedDamageDto.Code.REDECORATION -> RepairToUpdateOrAdd.TypeCode.DECO
  ReportedDamageDto.Code.CLEANING -> RepairToUpdateOrAdd.TypeCode.CLEA
  ReportedDamageDto.Code.REPLACE_AN_ITEM -> RepairToUpdateOrAdd.TypeCode.DECO // best match possibly?
}

private fun ReportedDamageDto.Code.toNomisCreateEnum(): RepairToCreate.TypeCode = when (this) {
  ReportedDamageDto.Code.ELECTRICAL_REPAIR -> RepairToCreate.TypeCode.ELEC
  ReportedDamageDto.Code.PLUMBING_REPAIR -> RepairToCreate.TypeCode.PLUM
  ReportedDamageDto.Code.FURNITURE_OR_FABRIC_REPAIR -> RepairToCreate.TypeCode.FABR
  ReportedDamageDto.Code.LOCK_REPAIR -> RepairToCreate.TypeCode.LOCK
  ReportedDamageDto.Code.REDECORATION -> RepairToCreate.TypeCode.DECO
  ReportedDamageDto.Code.CLEANING -> RepairToCreate.TypeCode.CLEA
  ReportedDamageDto.Code.REPLACE_AN_ITEM -> RepairToCreate.TypeCode.DECO
}

private fun ReportedEvidenceDto.toNomisCreateEvidence() = EvidenceToCreate(
  typeCode = when (this.code) {
    ReportedEvidenceDto.Code.PHOTO -> EvidenceToCreate.TypeCode.PHOTO
    ReportedEvidenceDto.Code.BODY_WORN_CAMERA -> EvidenceToCreate.TypeCode.OTHER
    ReportedEvidenceDto.Code.CCTV -> EvidenceToCreate.TypeCode.OTHER
    ReportedEvidenceDto.Code.BAGGED_AND_TAGGED -> EvidenceToCreate.TypeCode.EVI_BAG
    ReportedEvidenceDto.Code.OTHER -> EvidenceToCreate.TypeCode.OTHER
  },
  detail = this.details,
)

private fun ReportedEvidenceDto.toNomisUpdateEvidence() = EvidenceToUpdateOrAdd(
  typeCode = when (this.code) {
    ReportedEvidenceDto.Code.PHOTO -> EvidenceToUpdateOrAdd.TypeCode.PHOTO
    ReportedEvidenceDto.Code.BODY_WORN_CAMERA -> EvidenceToUpdateOrAdd.TypeCode.OTHER
    ReportedEvidenceDto.Code.CCTV -> EvidenceToUpdateOrAdd.TypeCode.OTHER
    ReportedEvidenceDto.Code.BAGGED_AND_TAGGED -> EvidenceToUpdateOrAdd.TypeCode.EVI_BAG
    ReportedEvidenceDto.Code.OTHER -> EvidenceToUpdateOrAdd.TypeCode.OTHER
  },
  detail = this.details,
)

private fun LocalDateTime.toLocalTimeAtMinute() = this.toLocalTime().withSecond(0).withNano(0)

data class AdjudicationAdditionalInformation(
  val chargeNumber: String,
  val prisonId: String,
  val prisonerNumber: String,
)

data class AdjudicationCreatedEvent(
  val additionalInformation: AdjudicationAdditionalInformation,
)

data class AdjudicationDamagesUpdateEvent(
  val additionalInformation: AdjudicationAdditionalInformation,
)

data class AdjudicationEvidenceUpdateEvent(
  val additionalInformation: AdjudicationAdditionalInformation,
)

private fun HearingDto.toNomisCreateHearing(nomisInternalLocationId: Long) = CreateHearingRequest(
  hearingType = this.oicHearingType.name,
  hearingDate = this.dateTimeOfHearing.toLocalDate(),
  hearingTime = this.dateTimeOfHearing.toLocalTimeAtMinute().toString(),
  agencyId = this.agencyId,
  internalLocationId = nomisInternalLocationId,
)

data class HearingAdditionalInformation(
  val chargeNumber: String,
  val prisonId: String,
  val prisonerNumber: String,
  val hearingId: String,
)

data class HearingEvent(
  val additionalInformation: HearingAdditionalInformation,
)

data class ReferralAdditionalInformation(
  val chargeNumber: String,
  val prisonId: String,
  val prisonerNumber: String,
)

data class ReferralEvent(
  val additionalInformation: ReferralAdditionalInformation,
)

data class OutcomeEvent(
  val additionalInformation: OutcomeAdditionalInformation,
)

data class OutcomeAdditionalInformation(
  val chargeNumber: String,
  val prisonId: String,
  val prisonerNumber: String,
  val hearingId: String?,
)

class InvalidFindingCodeException(message: String) : IllegalStateException(message)

data class PunishmentsAdditionalInformation(
  val chargeNumber: String,
  val prisonId: String,
  val prisonerNumber: String,
)

data class PunishmentEvent(
  val additionalInformation: PunishmentsAdditionalInformation,
)

class AdjudicationOutcomeInWrongState(val outcome: OutcomeDto.Code?) : IllegalStateException("Adjudication is in the wrong state. Outcome is $outcome")
