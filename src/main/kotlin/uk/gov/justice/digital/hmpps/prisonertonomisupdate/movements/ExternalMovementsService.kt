package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ParentEntityNotFoundRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.APPLICATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.OUTSIDE_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.SCHEDULED_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceOutsideMovementRequest
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
          val nomisApplicationId = mappingApiService.getApplicationMapping(dpsMovementApplicationId)
            ?.nomisMovementApplicationId
            ?.also { telemetryMap["nomisMovementApplicationId"] = it.toString() }
            ?: throw ParentEntityNotFoundRetry("Cannot find application mapping for $dpsMovementApplicationId")
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

  suspend fun scheduledMovementOutCreated(event: TemporaryAbsenceScheduledMovementEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsMovementApplicationId = event.additionalInformation.applicationId
    val dpsScheduledMovementId = event.additionalInformation.scheduledMovementId
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
          val nomisApplicationId = mappingApiService.getApplicationMapping(dpsMovementApplicationId)
            ?.nomisMovementApplicationId
            ?.also { telemetryMap["nomisMovementApplicationId"] = it.toString() }
            ?: throw ParentEntityNotFoundRetry("Cannot find application mapping for $dpsMovementApplicationId")
// TODO         val dps = dpsApiService.getTapOut(dpsId)
          val dps = ScheduledMovementOut.createData(dpsMovementApplicationId)
          val nomis = nomisApiService.createScheduledTemporaryAbsence(prisonerNumber, dps.toNomisCreateRequest(nomisApplicationId))
          val bookingId = nomis.bookingId
            .also { telemetryMap["bookingId"] = it.toString() }
          ScheduledMovementSyncMappingDto(
            prisonerNumber = prisonerNumber,
            bookingId = bookingId,
            nomisEventId = nomis.eventId,
            dpsScheduledMovementId = dpsScheduledMovementId,
            mappingType = ScheduledMovementSyncMappingDto.MappingType.DPS_CREATED,
          )
        }
        saveMapping { mappingApiService.createScheduledMovementMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("${SCHEDULED_MOVEMENT.entityName}-create-ignored", telemetryMap)
    }
  }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (MappingTypes.fromEntityName(baseMapping.entityName)) {
      APPLICATION -> createApplicationMapping(message.fromJson())
      OUTSIDE_MOVEMENT -> createOutsideMovementMapping(message.fromJson())
      SCHEDULED_MOVEMENT -> createScheduledMovementMapping(message.fromJson())
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

  fun toNomisCreateRequest() = CreateTemporaryAbsenceApplicationRequest(
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
    toAgencyId = toAgencyId,
    toAddressId = toAddressId,
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
