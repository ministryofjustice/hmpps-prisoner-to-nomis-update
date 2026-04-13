package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsMappingApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsRetryQueueService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsRetryService.Companion.MappingTypes.APPLICATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.TemporaryAbsenceEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapAuthorisationOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.createMapping
import java.util.*

private val AUTHORISATION_EVENTS_UPDATES_ONLY = listOf("person.temporary-absence-authorisation.relocated")

@Service
class TapAuthorisationService(
  private val mappingApiService: ExternalMovementsMappingApiService,
  private val nomisApiService: TapNomisApiService,
  private val dpsApiService: ExternalMovementsDpsApiService,
  private val retryQueueService: ExternalMovementsRetryQueueService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  companion object {
    private val TELEMETRY_KEY = "temporary-absence-application"
    private val TELEMETRY_KEY_CREATE = "$TELEMETRY_KEY-create"
    private val TELEMETRY_KEY_UPDATE = "$TELEMETRY_KEY-update"

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun authorisationChanged(event: TemporaryAbsenceEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsAuthorisationId = event.additionalInformation.id
    val source = event.additionalInformation.source
    val updatesOnly = event.eventType in AUTHORISATION_EVENTS_UPDATES_ONLY
    authorisationChanged(prisonerNumber, dpsAuthorisationId, source, updatesOnly)
  }

  suspend fun authorisationChanged(prisonerNumber: String, dpsAuthorisationId: UUID, source: String, updatesOnly: Boolean) {
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsAuthorisationId" to dpsAuthorisationId.toString(),
    )
    var telemetryKey: String = TELEMETRY_KEY_CREATE

    if (source != "DPS") {
      telemetryClient.trackEvent("$telemetryKey-ignored", telemetryMap)
      return
    }

    runCatching {
      val existingMapping = mappingApiService.getApplicationMapping(dpsAuthorisationId)
      if (existingMapping != null) telemetryKey = TELEMETRY_KEY_UPDATE
      if (existingMapping == null && updatesOnly) {
        telemetryClient.trackEvent(
          "$telemetryKey-ignored",
          telemetryMap + mapOf("reason" to "This event is applied to updates only"),
        )
        return
      }

      val dps = dpsApiService.getTapAuthorisation(dpsAuthorisationId)
      val addressScheduleMappings = dps.occurrences.findAddressScheduleMappings()
      val nomis = nomisApiService.upsertTemporaryAbsenceApplication(
        prisonerNumber,
        dps.toNomisUpsertRequest(existingMapping?.nomisMovementApplicationId, addressScheduleMappings),
      )
        .also { telemetryMap["bookingId"] = it.bookingId.toString() }
        .also { telemetryMap["nomisApplicationId"] = it.movementApplicationId.toString() }

      if (existingMapping == null) {
        TemporaryAbsenceApplicationSyncMappingDto(
          prisonerNumber = prisonerNumber,
          bookingId = nomis.bookingId,
          nomisMovementApplicationId = nomis.movementApplicationId,
          dpsMovementApplicationId = dpsAuthorisationId,
          mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.DPS_CREATED,
        )
          .also { mapping ->
            createMapping(
              mapping,
              telemetryClient,
              { createApplicationMapping(mapping, telemetryMap) },
              telemetryMap,
              retryQueueService,
              APPLICATION.entityName,
              log,
              failureSuffix = "error",
              failureReasonKey = "error",
            )
          }
      } else {
        // we're not creating the mapping so no need to wait for it before publishing success telemetry
        telemetryClient.trackEvent("$telemetryKey-success", telemetryMap)
      }
    }
      .onFailure {
        telemetryMap["error"] = it.message ?: "Unknown error"
        telemetryClient.trackEvent("$telemetryKey-error", telemetryMap)
        throw it
      }
  }

  suspend fun createApplicationMapping(message: CreateMappingRetryMessage<TemporaryAbsenceApplicationSyncMappingDto>) {
    createApplicationMapping(message.mapping, message.telemetryAttributes)
  }

  suspend fun createApplicationMapping(mapping: TemporaryAbsenceApplicationSyncMappingDto, telemetry: Map<String, String>) {
    mappingApiService.createApplicationMapping(mapping).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_KEY_CREATE-success",
        telemetry,
      )
    }
  }

  private fun SyncReadTapAuthorisation.toNomisUpsertRequest(
    nomisApplicationId: Long?,
    addressScheduleMappings: List<ScheduledMovementSyncMappingDto>,
  ): UpsertTemporaryAbsenceApplicationRequest {
    val releaseTime = when (repeat) {
      // If a schedule in NOMIS is deleted and re-created its start time is taken from the application releaseTime - so save it on the application
      false if (occurrences.size == 1) -> occurrences.first().start
      else -> start.atStartOfDay()
    }
    val returnTime = when (repeat) {
      // If a schedule in NOMIS is deleted and re-created its end time is taken from the application returnTime - so save it on the application
      false if (occurrences.size == 1) -> occurrences.first().end
      else -> end.plusDays(1).atStartOfDay().minusMinutes(1)
    }
    return UpsertTemporaryAbsenceApplicationRequest(
      movementApplicationId = nomisApplicationId,
      eventSubType = absenceReasonCode,
      applicationDate = created.at.toLocalDate(),
      fromDate = releaseTime.toLocalDate(),
      releaseTime = releaseTime,
      toDate = returnTime.toLocalDate(),
      returnTime = returnTime,
      applicationStatus = statusCode.toNomisApplicationStatus(occurrences.size),
      applicationType = if (repeat) "REPEATING" else "SINGLE",
      escortCode = if (accompaniedByCode == NULL_NOMIS_ESCORT_CODE) null else accompaniedByCode,
      transportType = transportCode,
      comment = comments,
      prisonId = prisonCode,
      temporaryAbsenceType = absenceTypeCode,
      temporaryAbsenceSubType = absenceSubTypeCode,
      toAddresses = occurrences.map { it.location.toNomisAddressRequest(addressScheduleMappings) }.toSet().toList(),
    )
  }

  private suspend fun List<SyncReadTapAuthorisationOccurrence>.findAddressScheduleMappings() = groupBy { it.location }
    .map { uniqueLocations -> uniqueLocations.value.first() }
    .map { occurrence -> occurrence.id }
    .mapNotNull { occurrenceId -> mappingApiService.getScheduledMovementMapping(occurrenceId) }

  private fun String.toNomisApplicationStatus(occurrenceCount: Int) = when (this) {
    "EXPIRED", "PENDING" -> "PEN"
    "APPROVED" if (occurrenceCount == 0) -> "APP-UNSCH"
    "APPROVED" -> "APP-SCH"
    "DENIED" -> "DEN"
    "CANCELLED" -> "CANC"
    else -> throw IllegalArgumentException("Unknown authorisation status: $this")
  }

  private fun Location.toNomisAddressRequest(knownAddresses: List<ScheduledMovementSyncMappingDto>) = knownAddresses.find { knownAddress ->
    address == knownAddress.dpsAddressText &&
      description == knownAddress.dpsDescription &&
      postcode == knownAddress.dpsPostcode
  }
    ?.takeIf { it.nomisAddressId != null }
    ?.let { UpsertTemporaryAbsenceAddress(id = it.nomisAddressId) }
    ?: UpsertTemporaryAbsenceAddress(name = description, addressText = address, postalCode = postcode)
}
