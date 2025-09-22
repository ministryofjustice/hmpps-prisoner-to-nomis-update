package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsService.Companion.MappingTypes.APPLICATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceApplicationRequest
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
      ;

      companion object {
        fun fromEntityName(entityName: String) = entries.find { it.entityName == entityName }
          ?: throw IllegalStateException("Mapping type $entityName does not exist")
      }
    }

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun applicationCreated(event: TemporaryAbsenceApplicationEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsId = event.additionalInformation.applicationId
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsApplicationId" to dpsId.toString(),
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
            .also { telemetryMap["nomisApplicationId"] = it.toString() }
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

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (MappingTypes.fromEntityName(baseMapping.entityName)) {
      APPLICATION -> createApplicationMapping(message.fromJson())
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
