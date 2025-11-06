package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ParentEntityNotFoundRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.APPLICATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.EXTERNAL_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.OUTSIDE_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.SCHEDULED_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceOutsideMovementRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Service
class ExternalMovementsService(
  private val mappingApiService: ExternalMovementsMappingApiService,
  private val nomisApiService: ExternalMovementsNomisApiService,
  private val retryQueueService: ExternalMovementsRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {
  private companion object {
    enum class MappingTypes(val entityName: String) {
      APPLICATION("temporary-absence-application"),
      OUTSIDE_MOVEMENT("temporary-absence-outside-movement"),
      SCHEDULED_MOVEMENT("temporary-absence-scheduled-movement"),
      EXTERNAL_MOVEMENT("temporary-absence-external-movement"),
      ;

      companion object {
        fun fromEntityName(entityName: String) = entries.find { it.entityName == entityName }
          ?: throw IllegalStateException("Mapping type $entityName does not exist")
      }
    }
  }

  suspend fun applicationCreated(event: TemporaryAbsenceApplicationEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsId = event.additionalInformation.applicationId
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsMovementApplicationId" to dpsId.toString(),
    )

    if (event.additionalInformation.source == "DPS") {
      synchronise {
        name = APPLICATION.entityName
        telemetryClient = this@ExternalMovementsService.telemetryClient
        retryQueueService = this@ExternalMovementsService.retryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getApplicationMapping(dpsId)
        }
        transform {
//          val dps = dpsApiService.getTapApplication(dpsId)
          val dps = TapApplication.CREATE_DATA
          val nomis = nomisApiService.createTemporaryAbsenceApplication(prisonerNumber, dps.toNomisCreateRequest())
          val bookingId = nomis.bookingId
            .also { telemetryMap["bookingId"] = it.toString() }
          val nomisApplicationId = nomis.movementApplicationId
            .also { telemetryMap["nomisMovementApplicationId"] = it.toString() }
          TemporaryAbsenceApplicationSyncMappingDto(
            prisonerNumber = prisonerNumber,
            bookingId = bookingId,
            nomisMovementApplicationId = nomisApplicationId,
            dpsMovementApplicationId = dpsId,
            mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.DPS_CREATED,
          )
        }
        saveMapping { mappingApiService.createApplicationMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("${APPLICATION.entityName}-create-ignored", telemetryMap)
    }
  }

  suspend fun outsideMovementCreated(event: TemporaryAbsenceOutsideMovementEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsMovementApplicationId = event.additionalInformation.applicationId
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

  suspend fun scheduledMovementOutCreated(event: TemporaryAbsenceScheduledMovementOutEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsMovementApplicationId = event.additionalInformation.applicationId
    val dpsScheduledMovementId = event.additionalInformation.scheduledMovementOutId
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsMovementApplicationId" to dpsMovementApplicationId.toString(),
      "dpsScheduledMovementId" to dpsScheduledMovementId.toString(),
      "direction" to "OUT",
    )

    if (event.additionalInformation.source == "DPS") {
      synchronise {
        name = SCHEDULED_MOVEMENT.entityName
        telemetryClient = this@ExternalMovementsService.telemetryClient
        retryQueueService = this@ExternalMovementsService.retryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getScheduledMovementMapping(dpsScheduledMovementId)
        }

        transform {
          val nomisApplicationId = findParentApplicationIdOrThrow(dpsMovementApplicationId)
            .also { telemetryMap["nomisMovementApplicationId"] = it.toString() }
// TODO         val dps = dpsApiService.getTapOut(dpsId)
          val dps = ScheduledMovementOut.createData(dpsMovementApplicationId)
          val nomis = nomisApiService.createScheduledTemporaryAbsence(prisonerNumber, dps.toNomisCreateRequest(nomisApplicationId))
          val bookingId = nomis.bookingId
            .also { telemetryMap["bookingId"] = it.toString() }
          ScheduledMovementSyncMappingDto(
            prisonerNumber = prisonerNumber,
            bookingId = bookingId,
            nomisEventId = nomis.eventId,
            dpsOccurrenceId = dpsScheduledMovementId,
            mappingType = ScheduledMovementSyncMappingDto.MappingType.DPS_CREATED,
            // TODO add address mapping details
            nomisAddressId = 0,
            nomisAddressOwnerClass = "",
            dpsAddressText = "",
            eventTime = "",
          )
        }
        saveMapping { mappingApiService.createScheduledMovementMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("${SCHEDULED_MOVEMENT.entityName}-create-ignored", telemetryMap)
    }
  }

  suspend fun scheduledMovementInCreated(event: TemporaryAbsenceScheduledMovementInEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsMovementApplicationId = event.additionalInformation.applicationId
    val dpsScheduledMovementOutId = event.additionalInformation.scheduledMovementOutId
    val dpsScheduledMovementInId = event.additionalInformation.scheduledMovementInId
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsMovementApplicationId" to dpsMovementApplicationId.toString(),
      "dpsScheduledMovementOutId" to dpsScheduledMovementOutId.toString(),
      "dpsScheduledMovementInId" to dpsScheduledMovementInId.toString(),
      "direction" to "IN",
    )

    if (event.additionalInformation.source == "DPS") {
      synchronise {
        name = SCHEDULED_MOVEMENT.entityName
        telemetryClient = this@ExternalMovementsService.telemetryClient
        retryQueueService = this@ExternalMovementsService.retryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getScheduledMovementMapping(dpsScheduledMovementInId)
        }

        transform {
          val nomisApplicationId = findParentApplicationIdOrThrow(dpsMovementApplicationId)
            .also { telemetryMap["nomisMovementApplicationId"] = it.toString() }
          val nomisScheduledMovementOutId = findParentScheduleOrThrow(dpsScheduledMovementOutId)
            .also { telemetryMap["nomisScheduledMovementOutId"] = it.toString() }
          // TODO         val dps = dpsApiService.getTapOut(dpsId)
          val dps = ScheduledMovementIn.createData(dpsMovementApplicationId, dpsScheduledMovementOutId)
          val nomis = nomisApiService.createScheduledTemporaryAbsenceReturn(prisonerNumber, dps.toNomisCreateRequest(nomisApplicationId, nomisScheduledMovementOutId))
          val bookingId = nomis.bookingId
            .also { telemetryMap["bookingId"] = it.toString() }
          ScheduledMovementSyncMappingDto(
            prisonerNumber = prisonerNumber,
            bookingId = bookingId,
            nomisEventId = nomis.eventId,
            dpsOccurrenceId = dpsScheduledMovementInId,
            mappingType = ScheduledMovementSyncMappingDto.MappingType.DPS_CREATED,
            // TODO add address mapping details
            nomisAddressId = 0,
            nomisAddressOwnerClass = "",
            dpsAddressText = "",
            eventTime = "",
          )
        }
        saveMapping { mappingApiService.createScheduledMovementMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("${SCHEDULED_MOVEMENT.entityName}-create-ignored", telemetryMap)
    }
  }

  suspend fun externalMovementOutCreated(event: TemporaryAbsenceExternalMovementOutEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsMovementApplicationId = event.additionalInformation.applicationId
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
    val dpsMovementApplicationId = event.additionalInformation.applicationId
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
    mappingApiService.createApplicationMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${APPLICATION.entityName}-create-success",
        message.telemetryAttributes,
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
    mappingApiService.createScheduledMovementMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${SCHEDULED_MOVEMENT.entityName}-create-success",
        message.telemetryAttributes,
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

// TODO drop this class and replace with DPS API model when the DPS API is available
data class TapApplication(
  val id: UUID,
  val eventSubType: String,
  val applicationDate: LocalDate,
  val fromDate: LocalDate,
  val releaseTime: LocalDateTime,
  val toDate: LocalDate,
  val returnTime: LocalDateTime,
  val applicationStatus: String,
  val applicationType: String,
  val escortCode: String? = null,
  val transportType: String? = null,
  val comment: String? = null,
  val prisonId: String? = null,
  val toAgencyId: String? = null,
  val toAddressId: Long? = null,
  val toAddressOwnerClass: String? = null,
  val contactPersonName: String? = null,
  val temporaryAbsenceType: String? = null,
  val temporaryAbsenceSubType: String? = null,
) {
  companion object {
    val CREATE_DATA = TapApplication(
      id = UUID.randomUUID(),
      eventSubType = "C5",
      applicationStatus = "APP",
      applicationType = "TAP",
      transportType = "CAR",
      applicationDate = LocalDate.now(),
      fromDate = LocalDate.now(),
      releaseTime = LocalDateTime.now(),
      toDate = LocalDate.now().plusDays(1),
      returnTime = LocalDateTime.now().plusDays(1),
      escortCode = "U",
      comment = "Tap Application Create comment",
      contactPersonName = "Deek Sanderson",
      toAgencyId = "HAZLWD",
      toAddressId = 3456,
      temporaryAbsenceType = "RR",
      temporaryAbsenceSubType = "RDR",
    )
  }

  fun toNomisCreateRequest() = UpsertTemporaryAbsenceApplicationRequest(
    eventSubType = eventSubType,
    fromDate = fromDate,
    applicationDate = applicationDate,
    releaseTime = releaseTime,
    toDate = toDate,
    returnTime = returnTime,
    applicationStatus = applicationStatus,
    applicationType = applicationType,
    escortCode = escortCode,
    transportType = transportType,
    comment = comment,
    prisonId = prisonId,
    temporaryAbsenceType = temporaryAbsenceType,
    temporaryAbsenceSubType = temporaryAbsenceSubType,
    contactPersonName = contactPersonName,
  )
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
data class ScheduledMovementOut(
  val id: UUID,
  val applicationId: UUID,
  val eventSubType: String,
  val eventStatus: String,
  val escortCode: String,
  val fromPrison: String,
  val applicationDate: LocalDate,
  val eventDate: LocalDate,
  val startTime: LocalDateTime,
  val returnDate: LocalDate,
  val returnTime: LocalDateTime,
  val comment: String? = null,
  val toAgencyId: String? = null,
  val toAddressId: Long? = null,
  val transportType: String? = null,
) {
  companion object {
    fun createData(applicationId: UUID) = ScheduledMovementOut(
      id = UUID.randomUUID(),
      applicationId = applicationId,
      eventSubType = "C5",
      eventStatus = "SCH",
      escortCode = "U",
      fromPrison = "LEI",
      applicationDate = LocalDate.now(),
      eventDate = LocalDate.now(),
      startTime = LocalDateTime.now(),
      returnDate = LocalDate.now().plusDays(1),
      returnTime = LocalDateTime.now().plusDays(1),
      comment = "Scheduled temporary absence comment",
      toAgencyId = "HAZLWD",
      toAddressId = 3456,
      transportType = "VAN",
    )
  }

  fun toNomisCreateRequest(nomisApplicationId: Long) = CreateScheduledTemporaryAbsenceRequest(
    movementApplicationId = nomisApplicationId,
    eventSubType = eventSubType,
    eventStatus = eventStatus,
    escort = escortCode,
    fromPrison = fromPrison,
    applicationDate = applicationDate.atTime(0, 0),
    eventDate = eventDate,
    startTime = startTime,
    returnDate = returnDate,
    returnTime = returnTime,
    comment = comment,
    toAgency = toAgencyId,
    toAddressId = toAddressId,
    transportType = transportType,
  )
}

// TODO drop this class and replace with DPS API model when the DPS API is available
data class ScheduledMovementIn(
  val id: UUID,
  val applicationId: UUID,
  val scheduledMovementOutId: UUID,
  val eventSubType: String,
  val eventStatus: String,
  val escortCode: String,
  val toPrison: String,
  val eventDate: LocalDate,
  val startTime: LocalDateTime,
  val comment: String? = null,
  val fromAgencyId: String? = null,
) {
  companion object {
    fun createData(applicationId: UUID, scheduledMovementOutId: UUID) = ScheduledMovementIn(
      id = UUID.randomUUID(),
      applicationId = applicationId,
      scheduledMovementOutId = scheduledMovementOutId,
      eventSubType = "C5",
      eventStatus = "SCH",
      escortCode = "U",
      toPrison = "LEI",
      eventDate = LocalDate.now(),
      startTime = LocalDateTime.now(),
      comment = "Scheduled temporary absence comment",
      fromAgencyId = "HAZLWD",
    )
  }

  fun toNomisCreateRequest(nomisApplicationId: Long, nomisScheduledMovementOutId: Long) = CreateScheduledTemporaryAbsenceReturnRequest(
    movementApplicationId = nomisApplicationId,
    scheduledTemporaryAbsenceEventId = nomisScheduledMovementOutId,
    eventSubType = eventSubType,
    eventStatus = eventStatus,
    escort = escortCode,
    toPrison = toPrison,
    eventDate = eventDate,
    startTime = startTime,
    comment = comment,
    fromAgency = fromAgencyId,
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
  val fromPrison: String?,
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
  val toPrison: String?,
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
