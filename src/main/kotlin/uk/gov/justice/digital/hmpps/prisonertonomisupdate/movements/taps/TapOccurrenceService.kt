package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsMappingApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapRetryService.Companion.MappingTypes.SCHEDULE_CREATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapRetryService.Companion.MappingTypes.SCHEDULE_UPDATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.AwaitParentEntityRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.createMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.track
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.tryFetchParent
import java.util.*

private val OCCURRENCE_EVENTS_UPDATE_AUTHORISATION: List<String> = listOf("person.temporary-absence.rescheduled")
internal val NULL_NOMIS_ESCORT_CODE = "NOT_PROVIDED"

@Service
class TapOccurrenceService(
  private val mappingApiService: ExternalMovementsMappingApiService,
  private val nomisApiService: TapNomisApiService,
  private val dpsApiService: TapDpsApiService,
  private val retryQueueService: TapRetryQueueService,
  override val telemetryClient: TelemetryClient,
  private val tapAuthorisationService: TapAuthorisationService,
) : TelemetryEnabled {

  companion object {
    private val TELEMETRY_KEY = "temporary-absence-schedule"
    private val TELEMETRY_KEY_CREATE = "$TELEMETRY_KEY-create"
    private val TELEMETRY_KEY_UPDATE = "$TELEMETRY_KEY-update"
    private val TELEMETRY_KEY_DELETE = "$TELEMETRY_KEY-delete"

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun occurrenceChanged(event: TemporaryAbsenceEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsOccurrenceId = event.additionalInformation.id
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsOccurrenceId" to dpsOccurrenceId.toString(),
    )
    var telemetryKey: String = TELEMETRY_KEY_CREATE

    if (event.additionalInformation.source != "DPS") {
      telemetryClient.trackEvent("$telemetryKey-ignored", telemetryMap)
      return
    }

    runCatching {
      val existingMapping = mappingApiService.getTapScheduleMapping(dpsOccurrenceId)
      if (existingMapping != null) telemetryKey = TELEMETRY_KEY_UPDATE
      val dps = dpsApiService.getTapOccurrence(dpsOccurrenceId)
        .also { telemetryMap["dpsAuthorisationId"] = "${it.authorisation.id}" }
      val nomisApplicationId = tryFetchParent {
        mappingApiService.getTapApplicationMapping(dps.authorisation.id)?.nomisApplicationId
      }.also { telemetryMap["nomisApplicationId"] = it.toString() }

      // perform the sync to NOMIS
      val nomis = nomisApiService.upsertTapScheduleOut(
        prisonerNumber,
        dps.toNomisUpsertRequest(nomisApplicationId, existingMapping),
      )
        .also { telemetryMap["bookingId"] = it.bookingId.toString() }
        .also { telemetryMap["nomisEventId"] = it.eventId.toString() }

      // create or update mappings
      when {
        existingMapping == null -> createScheduledMovementMapping(prisonerNumber, nomis, dpsOccurrenceId, dps, telemetryMap)
        existingMapping.nomisAddressId != nomis.addressId -> updateScheduledMovementMapping(existingMapping, nomis, dps, telemetryMap)
        else -> telemetryClient.trackEvent("$telemetryKey-success", telemetryMap)
      }

      // Synchronise the authorisation if required
      if (event.eventType in OCCURRENCE_EVENTS_UPDATE_AUTHORISATION) {
        tapAuthorisationService.authorisationChanged(prisonerNumber, dps.authorisation.id, "DPS", false)
      }
    }
      .onFailure {
        val failureType = if (it is AwaitParentEntityRetry) "awaiting-parent" else "error"
        telemetryMap["error"] = it.message ?: "Unknown error"
        telemetryClient.trackEvent("$telemetryKey-$failureType", telemetryMap)
        throw it
      }
  }

  suspend fun occurrenceDeleted(event: TemporaryAbsenceEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsOccurrenceId = event.additionalInformation.id
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsOccurrenceId" to dpsOccurrenceId.toString(),
    )

    if (event.additionalInformation.source != "DPS") {
      telemetryClient.trackEvent("$TELEMETRY_KEY_DELETE-ignored", telemetryMap)
      return
    }

    track(TELEMETRY_KEY_DELETE, telemetryMap) {
      val mapping = mappingApiService.getTapScheduleMapping(dpsOccurrenceId)
        ?.also { telemetryMap["nomisEventId"] = it.nomisEventId.toString() }
        ?: throw TapOccurrenceSyncException("Cannot find scheduled movement mapping for $dpsOccurrenceId")
      nomisApiService.deleteTapScheduleOut(prisonerNumber, mapping.nomisEventId)
    }
  }

  suspend fun createScheduledMovementMapping(message: CreateMappingRetryMessage<TapScheduleMappingDto>) {
    createScheduledMovementMapping(message.mapping, message.telemetryAttributes)
  }

  suspend fun createScheduledMovementMapping(mapping: TapScheduleMappingDto, telemetry: Map<String, String>) {
    mappingApiService.createTapScheduleMapping(mapping).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_KEY_CREATE-success",
        telemetry,
      )
    }
  }

  suspend fun updateScheduledMovementMapping(message: CreateMappingRetryMessage<TapScheduleMappingDto>) {
    updateScheduledMovementMapping(message.mapping, message.telemetryAttributes)
  }

  suspend fun updateScheduledMovementMapping(mapping: TapScheduleMappingDto, telemetry: Map<String, String>) {
    mappingApiService.updateTapScheduledMapping(mapping).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_KEY_UPDATE-success",
        telemetry,
      )
    }
  }

  private suspend fun createScheduledMovementMapping(
    prisonerNumber: String,
    nomis: UpsertTapScheduleOutResponse,
    dpsOccurrenceId: UUID,
    dps: SyncReadTapOccurrence,
    telemetryMap: MutableMap<String, String>,
  ): TapScheduleMappingDto = TapScheduleMappingDto(
    prisonerNumber = prisonerNumber,
    bookingId = nomis.bookingId,
    nomisEventId = nomis.eventId,
    dpsOccurrenceId = dpsOccurrenceId,
    mappingType = TapScheduleMappingDto.MappingType.DPS_CREATED,
    dpsAddressText = dps.location.address!!,
    eventTime = "${dps.start}",
    nomisAddressId = nomis.addressId,
    nomisAddressOwnerClass = nomis.addressOwnerClass,
    dpsUprn = dps.location.uprn,
    dpsDescription = dps.location.description,
    dpsPostcode = dps.location.postcode,
  )
    .also { mapping ->
      createMapping(
        mapping,
        telemetryClient,
        { createScheduledMovementMapping(mapping, telemetryMap) },
        telemetryMap,
        retryQueueService,
        SCHEDULE_CREATE.entityName,
        log,
        failureSuffix = "error",
        failureReasonKey = "error",
      )
    }

  private suspend fun updateScheduledMovementMapping(
    existingMapping: TapScheduleMappingDto,
    nomis: UpsertTapScheduleOutResponse,
    dps: SyncReadTapOccurrence,
    telemetryMap: MutableMap<String, String>,
  ): TapScheduleMappingDto = existingMapping.copy(
    nomisAddressId = nomis.addressId,
    nomisAddressOwnerClass = nomis.addressOwnerClass,
    dpsAddressText = dps.location.address!!,
    dpsUprn = dps.location.uprn,
    dpsPostcode = dps.location.postcode,
    dpsDescription = dps.location.description,
  )
    .also { mapping ->
      createMapping(
        mapping,
        telemetryClient,
        { updateScheduledMovementMapping(mapping, telemetryMap) },
        telemetryMap,
        retryQueueService,
        SCHEDULE_UPDATE.entityName,
        log,
        failureSuffix = "error",
        failureReasonKey = "error",
      )
    }

  private suspend fun SyncReadTapOccurrence.toNomisUpsertRequest(applicationId: Long, existingMapping: TapScheduleMappingDto?): UpsertTapScheduleOut {
    val (status, returnStatus) = this.statusCode.toNomisSchedulesStatus()
    return UpsertTapScheduleOut(
      tapApplicationId = applicationId,
      eventId = existingMapping?.nomisEventId,
      eventDate = this.start.toLocalDate(),
      startTime = this.start,
      eventSubType = this.absenceReasonCode,
      eventStatus = status,
      returnEventStatus = returnStatus,
      escort = if (accompaniedByCode == NULL_NOMIS_ESCORT_CODE) null else accompaniedByCode,
      fromPrison = authorisation.prisonCode,
      returnDate = this.end.toLocalDate(),
      returnTime = this.end,
      applicationDate = this.created.at,
      applicationTime = this.created.at,
      comment = this.comments,
      transportType = this.transportCode,
      toAddress = this.location.populateAddressMapping(existingMapping),
    )
  }

  fun String.toNomisSchedulesStatus() = when (this) {
    "PENDING" -> "PEN" to null
    "CANCELLED" -> "CANC" to null
    "DENIED" -> "DEN" to null
    "SCHEDULED" -> "SCH" to null
    "EXPIRED" -> "EXP" to null
    "OVERDUE" -> "COMP" to "SCH"
    "IN_PROGRESS" -> "COMP" to "SCH"
    "COMPLETED" -> "COMP" to "COMP"
    else -> throw IllegalArgumentException("Unknown occurrence status: $this")
  }

  private suspend fun Location.populateAddressMapping(
    existingMapping: TapScheduleMappingDto?,
  ): UpsertTapAddress {
    if (address.isNullOrEmpty()) throw TapOccurrenceSyncException("No address text received from DPS")

    return when {
      // New scheduled movement or new DPS address - tell NOMIS to make sure the address exists
      existingMapping == null -> UpsertTapAddress(name = description, addressText = address, postalCode = postcode)
      existingMapping.dpsAddressChanged(this) -> UpsertTapAddress(name = description, addressText = address, postalCode = postcode)
      // Address not changed - use the existing NOMIS address
      else -> UpsertTapAddress(id = existingMapping.nomisAddressId)
    }
  }

  private fun TapScheduleMappingDto.dpsAddressChanged(dpsLocation: Location) = dpsAddressText != dpsLocation.address || dpsPostcode != dpsLocation.postcode || dpsUprn != dpsLocation.uprn
}

class TapOccurrenceSyncException(message: String) : RuntimeException(message)
