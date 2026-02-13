package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ParentEntityNotFoundRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.APPLICATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.EXTERNAL_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.SCHEDULED_MOVEMENT_CREATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.SCHEDULED_MOVEMENT_UPDATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.createMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

private val AUTHORISATION_EVENTS_UPDATES_ONLY = listOf("person.temporary-absence-authorisation.relocated")

@Service
class ExternalMovementsService(
  private val mappingApiService: ExternalMovementsMappingApiService,
  private val nomisApiService: ExternalMovementsNomisApiService,
  private val dpsApiService: ExternalMovementsDpsApiService,
  private val retryQueueService: ExternalMovementsRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {
  private companion object {
    enum class MappingTypes(val entityName: String) {
      APPLICATION("temporary-absence-application"),
      SCHEDULED_MOVEMENT_CREATE("temporary-absence-schedule-create"),
      SCHEDULED_MOVEMENT_UPDATE("temporary-absence-schedule-update"),
      EXTERNAL_MOVEMENT("temporary-absence-external-movement"),
      ;

      companion object {
        fun fromEntityName(entityName: String) = entries.find { it.entityName == entityName }
          ?: throw IllegalStateException("Mapping type $entityName does not exist")
      }
    }

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun authorisationChanged(event: TemporaryAbsenceEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsAuthorisationId = event.additionalInformation.id
    val source = event.additionalInformation.source
    val updatesOnly = event.eventType in AUTHORISATION_EVENTS_UPDATES_ONLY
    authorisationChanged(prisonerNumber, dpsAuthorisationId, source, updatesOnly)
  }

  private suspend fun authorisationChanged(prisonerNumber: String, dpsAuthorisationId: UUID, source: String, updatesOnly: Boolean) {
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsAuthorisationId" to dpsAuthorisationId.toString(),
    )
    var updateType: String? = "create"

    if (source != "DPS") {
      telemetryClient.trackEvent("${APPLICATION.entityName}-create-ignored", telemetryMap)
      return
    }

    runCatching {
      val existingMapping = mappingApiService.getApplicationMapping(dpsAuthorisationId)
      if (existingMapping != null) updateType = "update"
      if (existingMapping == null && updatesOnly) {
        telemetryClient.trackEvent(
          "${APPLICATION.entityName}-$updateType-ignored",
          telemetryMap + mapOf("reason" to "This event is applied to updates only"),
        )
        return
      }

      val dps = dpsApiService.getTapAuthorisation(dpsAuthorisationId)
      val nomis = nomisApiService.upsertTemporaryAbsenceApplication(
        prisonerNumber,
        dps.toNomisUpsertRequest(existingMapping?.nomisMovementApplicationId),
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
            )
          }
      } else {
        // we're not creating the mapping so no need to wait for it before publishing success telemetry
        telemetryClient.trackEvent("${APPLICATION.entityName}-$updateType-success", telemetryMap)
      }
    }
      .onFailure {
        telemetryMap["reason"] = it.message ?: "Unknown error"
        telemetryClient.trackEvent("${APPLICATION.entityName}-$updateType-failed", telemetryMap)
        throw it
      }
  }

  private suspend fun findParentApplicationIdOrThrow(
    dpsAuthorisationId: UUID,
  ): Long = mappingApiService.getApplicationMapping(dpsAuthorisationId)
    ?.nomisMovementApplicationId
    ?: throw ParentEntityNotFoundRetry("Cannot find parent application mapping for $dpsAuthorisationId")

  suspend fun occurrenceChanged(event: TemporaryAbsenceEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsOccurrenceId = event.additionalInformation.id
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsOccurrenceId" to dpsOccurrenceId.toString(),
    )
    var updateType: MappingTypes = SCHEDULED_MOVEMENT_CREATE

    if (event.additionalInformation.source != "DPS") {
      telemetryClient.trackEvent("${updateType.entityName}-ignored", telemetryMap)
      return
    }

    runCatching {
      val existingMapping = mappingApiService.getScheduledMovementMapping(dpsOccurrenceId)
      if (existingMapping != null) updateType = SCHEDULED_MOVEMENT_UPDATE
      val dps = dpsApiService.getTapOccurrence(dpsOccurrenceId)
        .also { telemetryMap["dpsAuthorisationId"] = "${it.authorisation.id}" }
      val nomisApplicationId = findParentApplicationIdOrThrow(dps.authorisation.id)
        .also { telemetryMap["nomisApplicationId"] = it.toString() }

      // perform the sync to NOMIS
      val nomis = nomisApiService.upsertScheduledTemporaryAbsence(
        prisonerNumber,
        dps.toNomisUpsertRequest(nomisApplicationId, existingMapping),
      )
        .also { telemetryMap["bookingId"] = it.bookingId.toString() }
        .also { telemetryMap["nomisEventId"] = it.eventId.toString() }

      // create or update mappings
      when {
        existingMapping == null -> createScheduledMovementMapping(prisonerNumber, nomis, dpsOccurrenceId, dps, telemetryMap)
        existingMapping.nomisAddressId != nomis.addressId -> updateScheduledMovementMapping(existingMapping, nomis, dps, telemetryMap)
        else -> telemetryClient.trackEvent("${updateType.entityName}-success", telemetryMap)
      }
    }
      .onFailure {
        telemetryMap["reason"] = it.message ?: "Unknown error"
        telemetryClient.trackEvent("${updateType.entityName}-failed", telemetryMap)
        throw it
      }
  }

  private suspend fun createScheduledMovementMapping(
    prisonerNumber: String,
    nomis: UpsertScheduledTemporaryAbsenceResponse,
    dpsOccurrenceId: UUID,
    dps: SyncReadTapOccurrence,
    telemetryMap: MutableMap<String, String>,
  ): ScheduledMovementSyncMappingDto = ScheduledMovementSyncMappingDto(
    prisonerNumber = prisonerNumber,
    bookingId = nomis.bookingId,
    nomisEventId = nomis.eventId,
    dpsOccurrenceId = dpsOccurrenceId,
    mappingType = ScheduledMovementSyncMappingDto.MappingType.DPS_CREATED,
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
        SCHEDULED_MOVEMENT_CREATE.entityName,
        log,
      )
    }

  private suspend fun updateScheduledMovementMapping(
    existingMapping: ScheduledMovementSyncMappingDto,
    nomis: UpsertScheduledTemporaryAbsenceResponse,
    dps: SyncReadTapOccurrence,
    telemetryMap: MutableMap<String, String>,
  ): ScheduledMovementSyncMappingDto = existingMapping.copy(
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
        SCHEDULED_MOVEMENT_UPDATE.entityName,
        log,
      )
    }

  suspend fun externalMovementOutCreated(event: TemporaryAbsenceExternalMovementOutEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsMovementApplicationId = event.additionalInformation.authorisationId
    val dpsScheduledMovementOutId = event.additionalInformation.scheduledMovementOutId
    val dpsExternalMovementId = event.additionalInformation.externalMovementOutId
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsExternalMovementId" to dpsExternalMovementId.toString(),
      "direction" to "OUT",
    ).apply {
      dpsMovementApplicationId?.also { this["dpsMovementApplicationId"] = it.toString() }
      dpsScheduledMovementOutId?.also { this["dpsScheduledMovementOutId"] = it.toString() }
    }

    if (event.additionalInformation.source == "DPS") {
      synchronise {
        name = EXTERNAL_MOVEMENT.entityName
        telemetryClient = this@ExternalMovementsService.telemetryClient
        retryQueueService = this@ExternalMovementsService.retryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getExternalMovementMapping(dpsExternalMovementId)
        }

        transform {
          dpsMovementApplicationId?.let {
            findParentApplicationIdOrThrow(dpsMovementApplicationId)
              .also { telemetryMap["nomisMovementApplicationId"] = it.toString() }
          }
          val nomisEventId = dpsScheduledMovementOutId?.let {
            findParentScheduleOrThrow(dpsScheduledMovementOutId)
              .also { telemetryMap["nomisEventId"] = it.toString() }
          }
// TODO         val dps = dpsApiService.getTapOut(dpsId)
          val dps = ExternalMovementOut.createData(dpsMovementApplicationId, dpsScheduledMovementOutId)
          val nomis = nomisApiService.createTemporaryAbsence(prisonerNumber, dps.toNomisCreateRequest(nomisEventId))
          val bookingId = nomis.bookingId
            .also { telemetryMap["bookingId"] = it.toString() }
          ExternalMovementSyncMappingDto(
            prisonerNumber = prisonerNumber,
            bookingId = bookingId,
            nomisMovementSeq = nomis.movementSequence,
            dpsMovementId = dpsExternalMovementId,
            mappingType = ExternalMovementSyncMappingDto.MappingType.DPS_CREATED,
            // TODO add address mapping details
            nomisAddressId = 0,
            nomisAddressOwnerClass = "",
            dpsAddressText = "",
          )
        }
        saveMapping { mappingApiService.createExternalMovementMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("${EXTERNAL_MOVEMENT.entityName}-create-ignored", telemetryMap)
    }
  }

  suspend fun externalMovementInCreated(event: TemporaryAbsenceExternalMovementInEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsMovementApplicationId = event.additionalInformation.authorisationId
    val dpsScheduledMovementInId = event.additionalInformation.scheduledMovementInId
    val dpsExternalMovementId = event.additionalInformation.externalMovementInId
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsExternalMovementId" to dpsExternalMovementId.toString(),
      "direction" to "IN",
    ).apply {
      dpsMovementApplicationId?.also { this["dpsMovementApplicationId"] = it.toString() }
      dpsScheduledMovementInId?.also { this["dpsScheduledMovementInId"] = it.toString() }
    }

    if (event.additionalInformation.source == "DPS") {
      synchronise {
        name = EXTERNAL_MOVEMENT.entityName
        telemetryClient = this@ExternalMovementsService.telemetryClient
        retryQueueService = this@ExternalMovementsService.retryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getExternalMovementMapping(dpsExternalMovementId)
        }

        transform {
          dpsMovementApplicationId?.let {
            findParentApplicationIdOrThrow(dpsMovementApplicationId)
              .also { telemetryMap["nomisMovementApplicationId"] = it.toString() }
          }
          val nomisEventId = dpsScheduledMovementInId?.let {
            findParentScheduleOrThrow(dpsScheduledMovementInId)
              .also { telemetryMap["nomisEventId"] = it.toString() }
          }
// TODO         val dps = dpsApiService.getTapIn(dpsId)
          val dps = ExternalMovementIn.createData(dpsMovementApplicationId, dpsScheduledMovementInId)
          val nomis = nomisApiService.createTemporaryAbsenceReturn(prisonerNumber, dps.toNomisCreateRequest(nomisEventId))
          val bookingId = nomis.bookingId
            .also { telemetryMap["bookingId"] = it.toString() }
          ExternalMovementSyncMappingDto(
            prisonerNumber = prisonerNumber,
            bookingId = bookingId,
            nomisMovementSeq = nomis.movementSequence,
            dpsMovementId = dpsExternalMovementId,
            mappingType = ExternalMovementSyncMappingDto.MappingType.DPS_CREATED,
            // TODO add address mapping details
            nomisAddressId = 0,
            nomisAddressOwnerClass = "",
            dpsAddressText = "",
          )
        }
        saveMapping { mappingApiService.createExternalMovementMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("${EXTERNAL_MOVEMENT.entityName}-create-ignored", telemetryMap)
    }
  }

  private suspend fun findParentScheduleOrThrow(
    dpsScheduledMovementOutId: UUID,
  ): Long = mappingApiService.getScheduledMovementMapping(dpsScheduledMovementOutId)
    ?.nomisEventId
    ?: throw ParentEntityNotFoundRetry("Cannot find scheduled movement mapping for $dpsScheduledMovementOutId")

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (MappingTypes.fromEntityName(baseMapping.entityName)) {
      APPLICATION -> createApplicationMapping(message.fromJson())
      SCHEDULED_MOVEMENT_CREATE -> createScheduledMovementMapping(message.fromJson())
      SCHEDULED_MOVEMENT_UPDATE -> updateScheduledMovementMapping(message.fromJson())
      EXTERNAL_MOVEMENT -> createExternalMovementMapping(message.fromJson())
    }
  }

  suspend fun createApplicationMapping(message: CreateMappingRetryMessage<TemporaryAbsenceApplicationSyncMappingDto>) {
    createApplicationMapping(message.mapping, message.telemetryAttributes)
  }

  suspend fun createApplicationMapping(mapping: TemporaryAbsenceApplicationSyncMappingDto, telemetry: Map<String, String>) {
    mappingApiService.createApplicationMapping(mapping).also {
      telemetryClient.trackEvent(
        "${APPLICATION.entityName}-create-success",
        telemetry,
      )
    }
  }

  suspend fun createScheduledMovementMapping(message: CreateMappingRetryMessage<ScheduledMovementSyncMappingDto>) {
    createScheduledMovementMapping(message.mapping, message.telemetryAttributes)
  }

  suspend fun createScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto, telemetry: Map<String, String>) {
    mappingApiService.createScheduledMovementMapping(mapping).also {
      telemetryClient.trackEvent(
        "${SCHEDULED_MOVEMENT_CREATE.entityName}-success",
        telemetry,
      )
    }
  }

  suspend fun updateScheduledMovementMapping(message: CreateMappingRetryMessage<ScheduledMovementSyncMappingDto>) {
    updateScheduledMovementMapping(message.mapping, message.telemetryAttributes)
  }

  suspend fun updateScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto, telemetry: Map<String, String>) {
    mappingApiService.updateScheduledMovementMapping(mapping).also {
      telemetryClient.trackEvent(
        "${SCHEDULED_MOVEMENT_UPDATE.entityName}-success",
        telemetry,
      )
    }
  }

  suspend fun createExternalMovementMapping(message: CreateMappingRetryMessage<ExternalMovementSyncMappingDto>) {
    mappingApiService.createExternalMovementMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${EXTERNAL_MOVEMENT.entityName}-create-success",
        message.telemetryAttributes,
      )
    }
  }

  private suspend fun SyncReadTapOccurrence.toNomisUpsertRequest(applicationId: Long, existingMapping: ScheduledMovementSyncMappingDto?): UpsertScheduledTemporaryAbsenceRequest {
    val (status, returnStatus) = this.statusCode.toNomisSchedulesStatus()
    return UpsertScheduledTemporaryAbsenceRequest(
      movementApplicationId = applicationId,
      eventId = existingMapping?.nomisEventId,
      eventDate = this.start.toLocalDate(),
      startTime = this.start,
      eventSubType = this.absenceReasonCode,
      eventStatus = status,
      returnEventStatus = returnStatus,
      escort = accompaniedByCode,
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

  private suspend fun Location.populateAddressMapping(
    existingMapping: ScheduledMovementSyncMappingDto?,
  ): UpsertTemporaryAbsenceAddress {
    if (address.isNullOrEmpty()) throw ExternalMovementsSyncException("No address text received from DPS")

    return when {
      // New scheduled movement or new DPS address - tell NOMIS to make sure the address exists
      existingMapping == null -> UpsertTemporaryAbsenceAddress(name = description, addressText = address, postalCode = postcode)
      existingMapping.dpsAddressChanged(this) -> UpsertTemporaryAbsenceAddress(name = description, addressText = address, postalCode = postcode)
      // Address not changed - use the existing NOMIS address
      else -> UpsertTemporaryAbsenceAddress(id = existingMapping.nomisAddressId)
    }
  }

  private fun ScheduledMovementSyncMappingDto.dpsAddressChanged(dpsLocation: Location) = dpsAddressText != dpsLocation.address || dpsPostcode != dpsLocation.postcode || dpsUprn != dpsLocation.uprn

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

private fun SyncReadTapAuthorisation.toNomisUpsertRequest(nomisApplicationId: Long?): UpsertTemporaryAbsenceApplicationRequest {
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
    escortCode = if (accompaniedByCode == "NOT_PROVIDED") null else accompaniedByCode,
    transportType = transportCode,
    comment = comments,
    prisonId = prisonCode,
    temporaryAbsenceType = absenceTypeCode,
    temporaryAbsenceSubType = absenceSubTypeCode,
    toAddresses = occurrences.map {
      with(it.location) {
        UpsertTemporaryAbsenceAddress(name = description, addressText = address, postalCode = postcode)
      }
    }.toSet().toList(),
  )
}

private fun String.toNomisApplicationStatus(occurrenceCount: Int) = when (this) {
  "EXPIRED", "PENDING" -> "PEN"
  "APPROVED" if (occurrenceCount == 0) -> "APP-UNSCH"
  "APPROVED" -> "APP-SCH"
  "DENIED" -> "DEN"
  "CANCELLED" -> "CANC"
  else -> throw IllegalArgumentException("Unknown authorisation status: $this")
}

private fun String.toNomisSchedulesStatus() = when (this) {
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

// TODO drop this class and replace with DPS API model when the DPS API is available
data class ExternalMovementOut(
  val id: UUID,
  val applicationId: UUID?,
  val scheduledMovementOutId: UUID?,
  val movementDate: LocalDate,
  val movementTime: LocalDateTime,
  val movementReason: String,
  val arrestAgency: String?,
  val escort: String?,
  val escortText: String?,
  val fromPrison: String,
  val toAgency: String?,
  val commentText: String?,
  val toCity: String?,
  val toAddressId: Long?,
) {
  companion object {
    fun createData(applicationId: UUID? = null, scheduledMovementOutId: UUID? = null) = ExternalMovementOut(
      id = UUID.randomUUID(),
      applicationId = applicationId,
      scheduledMovementOutId = scheduledMovementOutId,
      movementDate = LocalDate.now(),
      movementTime = LocalDateTime.now(),
      movementReason = "C5",
      arrestAgency = "POL",
      escort = "U",
      escortText = "Temporary absence escort text",
      fromPrison = "LEI",
      toAgency = "HAZLWD",
      commentText = "Temporary absence comment",
      toCity = "765",
      toAddressId = 76543L,
    )
  }

  fun toNomisCreateRequest(nomisEventId: Long? = null) = CreateTemporaryAbsenceRequest(
    scheduledTemporaryAbsenceId = nomisEventId,
    movementDate = movementDate,
    movementTime = movementTime,
    movementReason = movementReason,
    arrestAgency = arrestAgency,
    escort = escort,
    escortText = escortText,
    fromPrison = fromPrison,
    toAgency = toAgency,
    commentText = commentText,
    toCity = toCity,
    toAddressId = toAddressId,
  )
}

// TODO drop this class and replace with DPS API model when the DPS API is available
data class ExternalMovementIn(
  val id: UUID,
  val applicationId: UUID?,
  val scheduledMovementInId: UUID?,
  val movementDate: LocalDate,
  val movementTime: LocalDateTime,
  val movementReason: String,
  val arrestAgency: String?,
  val escort: String?,
  val escortText: String?,
  val toPrison: String,
  val fromAgency: String?,
  val commentText: String?,
  val fromAddressId: Long?,
) {
  companion object {
    fun createData(applicationId: UUID? = null, scheduledMovementInId: UUID? = null) = ExternalMovementIn(
      id = UUID.randomUUID(),
      applicationId = applicationId,
      scheduledMovementInId = scheduledMovementInId,
      movementDate = LocalDate.now(),
      movementTime = LocalDateTime.now(),
      movementReason = "C5",
      arrestAgency = "POL",
      escort = "U",
      escortText = "Temporary absence escort text",
      toPrison = "LEI",
      fromAgency = "HAZLWD",
      commentText = "Temporary absence comment",
      fromAddressId = 76543L,
    )
  }

  fun toNomisCreateRequest(nomisEventId: Long? = null) = CreateTemporaryAbsenceReturnRequest(
    scheduledTemporaryAbsenceReturnId = nomisEventId,
    movementDate = movementDate,
    movementTime = movementTime,
    movementReason = movementReason,
    arrestAgency = arrestAgency,
    escort = escort,
    escortText = escortText,
    toPrison = toPrison,
    fromAgency = fromAgency,
    commentText = commentText,
    fromAddressId = fromAddressId,
  )
}

data class ExternalMovementsSyncException(override val message: String) : RuntimeException(message)
