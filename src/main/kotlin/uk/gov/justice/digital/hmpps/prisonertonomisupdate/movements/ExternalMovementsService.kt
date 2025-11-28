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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.OUTSIDE_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.SCHEDULED_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceOutsideMovementRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.createMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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
      OUTSIDE_MOVEMENT("temporary-absence-outside-movement"),
      SCHEDULED_MOVEMENT("temporary-absence-schedule"),
      EXTERNAL_MOVEMENT("temporary-absence-external-movement"),
      ;

      companion object {
        fun fromEntityName(entityName: String) = entries.find { it.entityName == entityName }
          ?: throw IllegalStateException("Mapping type $entityName does not exist")
      }
    }

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun authorisationApproved(event: TemporaryAbsenceAuthorisationEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsId = event.additionalInformation.authorisationId
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsAuthorisationId" to dpsId.toString(),
    )
    var updateType: String? = "create"

    if (event.additionalInformation.source != "DPS") {
      telemetryClient.trackEvent("${APPLICATION.entityName}-create-ignored", telemetryMap)
      return
    }

    runCatching {
      val existingMapping = mappingApiService.getApplicationMapping(dpsId)
      if (existingMapping != null) updateType = "update"
      val dps = dpsApiService.getTapAuthorisation(dpsId)
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
          dpsMovementApplicationId = dpsId,
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
        telemetryClient.trackEvent("${APPLICATION.entityName}-$updateType-failed", telemetryMap)
        throw it
      }
  }

  suspend fun outsideMovementCreated(event: TemporaryAbsenceOutsideMovementEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsMovementApplicationId = event.additionalInformation.authorisationId
    val dpsOutsideMovementId = event.additionalInformation.outsideMovementId
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsMovementApplicationId" to dpsMovementApplicationId.toString(),
      "dpsOutsideMovementId" to dpsOutsideMovementId.toString(),
    )

    if (event.additionalInformation.source == "DPS") {
      synchronise {
        name = OUTSIDE_MOVEMENT.entityName
        telemetryClient = this@ExternalMovementsService.telemetryClient
        retryQueueService = this@ExternalMovementsService.retryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getOutsideMovementMapping(dpsOutsideMovementId)
        }

        transform {
          val nomisApplicationId = findParentApplicationIdOrThrow(dpsMovementApplicationId)
            .also { telemetryMap["nomisMovementApplicationId"] = it.toString() }
          // TODO         val dps = dpsApiService.getTapApplication(dpsId)
          val dps = OutsideMovement.createData(dpsMovementApplicationId)
          val nomis = nomisApiService.createTemporaryAbsenceOutsideMovement(prisonerNumber, dps.toNomisCreateRequest(nomisApplicationId))
          val bookingId = nomis.bookingId
            .also { telemetryMap["bookingId"] = it.toString() }
          TemporaryAbsenceOutsideMovementSyncMappingDto(
            prisonerNumber = prisonerNumber,
            bookingId = bookingId,
            nomisMovementApplicationMultiId = nomis.outsideMovementId,
            dpsOutsideMovementId = dpsOutsideMovementId,
            mappingType = TemporaryAbsenceOutsideMovementSyncMappingDto.MappingType.DPS_CREATED,
          )
        }
        saveMapping { mappingApiService.createOutsideMovementMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("${OUTSIDE_MOVEMENT.entityName}-create-ignored", telemetryMap)
    }
  }

  private suspend fun findParentApplicationIdOrThrow(
    dpsMovementApplicationId: UUID,
  ): Long = mappingApiService.getApplicationMapping(dpsMovementApplicationId)
    ?.nomisMovementApplicationId
    ?: throw ParentEntityNotFoundRetry("Cannot find application mapping for $dpsMovementApplicationId")

  suspend fun occurrenceChanged(event: TapOccurrenceEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsOccurrenceId = event.additionalInformation.occurrenceId
    val telemetryMap = mutableMapOf(
      "prisonerNumber" to prisonerNumber,
      "dpsOccurrenceId" to dpsOccurrenceId.toString(),
    )
    var updateType: String? = "create"

    if (event.additionalInformation.source != "DPS") {
      telemetryClient.trackEvent("${SCHEDULED_MOVEMENT.entityName}-create-ignored", telemetryMap)
    }

    runCatching {
      val existingMapping = mappingApiService.getScheduledMovementMapping(dpsOccurrenceId)
      if (existingMapping != null) updateType = "update"
      val dps = dpsApiService.getTapOccurrence(dpsOccurrenceId)
        .also { telemetryMap["dpsAuthorisationId"] = "${it.authorisation.id}" }
      val applicationMapping = mappingApiService.getApplicationMapping(dps.authorisation.id)
        ?.also { telemetryMap["nomisApplicationId"] = it.nomisMovementApplicationId.toString() }
        ?: throw ParentEntityNotFoundRetry("Cannot find application mapping for ${dps.authorisation.id}")
      val nomis = nomisApiService.upsertScheduledTemporaryAbsence(
        prisonerNumber,
        dps.toNomisUpsertRequest(applicationMapping.nomisMovementApplicationId, existingMapping),
      )
        .also { telemetryMap["bookingId"] = it.bookingId.toString() }
        .also { telemetryMap["nomisEventId"] = it.eventId.toString() }
      if (existingMapping == null) {
        ScheduledMovementSyncMappingDto(
          prisonerNumber = prisonerNumber,
          bookingId = nomis.bookingId,
          nomisEventId = nomis.eventId,
          dpsOccurrenceId = dpsOccurrenceId,
          mappingType = ScheduledMovementSyncMappingDto.MappingType.DPS_CREATED,
          dpsAddressText = dps.location.address ?: "",
          eventTime = "${dps.releaseAt}",
          // TODO nomisAddressId = nomis.toAddressId,
          // TODO nomisAddressOwnerClass = nomis.toAddressOwnerClass,
        )
          .also { mapping ->
            createMapping(
              mapping,
              telemetryClient,
              { createScheduledMovementMapping(mapping, telemetryMap) },
              telemetryMap,
              retryQueueService,
              SCHEDULED_MOVEMENT.entityName,
              log,
            )
          }
      } else {
        // TODO instead of this telemetry, update the mapping and publish telemetry there (update mapping in case the NOMIS address has changed)
        telemetryClient.trackEvent("${SCHEDULED_MOVEMENT.entityName}-$updateType-success", telemetryMap)
      }
    }
      .onFailure {
        telemetryClient.trackEvent("${SCHEDULED_MOVEMENT.entityName}-$updateType-failed", telemetryMap)
        throw it
      }
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
      OUTSIDE_MOVEMENT -> createOutsideMovementMapping(message.fromJson())
      SCHEDULED_MOVEMENT -> createScheduledMovementMapping(message.fromJson())
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

  suspend fun createOutsideMovementMapping(message: CreateMappingRetryMessage<TemporaryAbsenceOutsideMovementSyncMappingDto>) {
    mappingApiService.createOutsideMovementMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${OUTSIDE_MOVEMENT.entityName}-create-success",
        message.telemetryAttributes,
      )
    }
  }

  suspend fun createScheduledMovementMapping(message: CreateMappingRetryMessage<ScheduledMovementSyncMappingDto>) {
    createScheduledMovementMapping(message.mapping, message.telemetryAttributes)
  }

  suspend fun createScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto, telemetry: Map<String, String>) {
    mappingApiService.createScheduledMovementMapping(mapping).also {
      telemetryClient.trackEvent(
        "${SCHEDULED_MOVEMENT.entityName}-create-success",
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

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

private fun SyncReadTapAuthorisation.toNomisUpsertRequest(nomisApplicationId: Long?) = UpsertTemporaryAbsenceApplicationRequest(
  movementApplicationId = nomisApplicationId,
  eventSubType = absenceReasonCode,
  fromDate = fromDate,
  applicationDate = created.at.toLocalDate(),
  releaseTime = fromDate.atStartOfDay(),
  toDate = toDate,
  returnTime = toDate.plusDays(1).atStartOfDay(),
  applicationStatus = statusCode.toNomisApplicationStatus(occurrences.size),
  applicationType = if (repeat) "REPEATING" else "SINGLE",
  escortCode = occurrences.firstOrNull()?.accompaniedByCode,
  transportType = occurrences.firstOrNull()?.transportCode,
  comment = notes,
  prisonId = prisonCode,
  temporaryAbsenceType = absenceTypeCode,
  temporaryAbsenceSubType = absenceSubTypeCode,
)

private fun String.toNomisApplicationStatus(occurrenceCount: Int) = when (this) {
  "PENDING" -> "PEN"
  "APPROVED" if (occurrenceCount == 0) -> "APP-UNSCH"
  "APPROVED" -> "APP-SCH"
  "DENIED" -> "DEN"
  "CANCELLED" -> "CAN"
  else -> throw IllegalArgumentException("Unknown application status: $this")
}

private fun SyncReadTapOccurrence.toNomisUpsertRequest(applicationId: Long, existingMapping: ScheduledMovementSyncMappingDto?): UpsertScheduledTemporaryAbsenceRequest {
  val (status, returnStatus) = this.statusCode.toNomisOccurrenceStatus()
  return UpsertScheduledTemporaryAbsenceRequest(
    movementApplicationId = applicationId,
    eventId = existingMapping?.nomisEventId,
    eventDate = this.releaseAt.toLocalDate(),
    startTime = this.releaseAt,
    eventSubType = this.absenceReasonCode,
    eventStatus = status,
    returnEventStatus = returnStatus,
    escort = accompaniedByCode,
    fromPrison = authorisation.prisonCode,
    returnDate = this.returnBy.toLocalDate(),
    returnTime = this.returnBy,
    applicationDate = this.created.at,
    applicationTime = this.created.at,
    comment = this.notes,
    transportType = this.transportCode,
    // TODO SDIT-3032 populate address request. Only send addressId if new (no existing mapping), dps address details have  changed or the address owner class as derived from absence reason has changed.
    toAddress = UpsertTemporaryAbsenceAddress(),
  )
}

private fun String.toNomisOccurrenceStatus() = when (this) {
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
data class OutsideMovement(
  val id: UUID,
  val applicationId: UUID,
  val eventSubType: String,
  val applicationDate: LocalDate,
  val fromDate: LocalDate,
  val releaseTime: LocalDateTime,
  val toDate: LocalDate,
  val returnTime: LocalDateTime,
  val comment: String? = null,
  val toAgencyId: String? = null,
  val toAddressId: Long? = null,
  val contactPersonName: String? = null,
  val temporaryAbsenceType: String? = null,
  val temporaryAbsenceSubType: String? = null,
) {
  companion object {
    fun createData(applicationId: UUID) = OutsideMovement(
      id = UUID.randomUUID(),
      applicationId = applicationId,
      eventSubType = "C5",
      applicationDate = LocalDate.now(),
      fromDate = LocalDate.now(),
      releaseTime = LocalDateTime.now(),
      toDate = LocalDate.now().plusDays(1),
      returnTime = LocalDateTime.now().plusDays(1),
      comment = "Outside Movement Create comment",
      contactPersonName = "Deek Sanderson",
      toAgencyId = "HAZLWD",
      toAddressId = 3456,
      temporaryAbsenceType = "RR",
      temporaryAbsenceSubType = "RDR",
    )
  }

  fun toNomisCreateRequest(nomisApplicationId: Long) = CreateTemporaryAbsenceOutsideMovementRequest(
    movementApplicationId = nomisApplicationId,
    eventSubType = eventSubType,
    fromDate = fromDate,
    releaseTime = releaseTime,
    toDate = toDate,
    returnTime = returnTime,
    comment = comment,
    toAgencyId = toAgencyId,
    toAddressId = toAddressId,
    temporaryAbsenceType = temporaryAbsenceType,
    temporaryAbsenceSubType = temporaryAbsenceSubType,
    contactPersonName = contactPersonName,
  )
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
