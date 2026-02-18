package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.VisitsConfigurationResourceApi.DayOfWeekCreateVisitTimeSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.MappingRetry.MappingTypes.TIME_SLOT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.MappingRetry.MappingTypes.VISIT_SLOT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.DayType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.DayType.FRI
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.DayType.MON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.DayType.SAT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.DayType.SUN
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.DayType.THU
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.DayType.TUE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.DayType.WED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncTimeSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Service
class VisitSlotsService(
  private val telemetryClient: TelemetryClient,
  private val mappingApiService: VisitSlotsMappingService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  private val nomisApiService: VisitSlotsNomisApiService,
  private val officialVisitsRetryQueueService: OfficialVisitsRetryQueueService,
) {

  suspend fun timeSlotCreated(event: TimeSlotEvent) {
    val telemetryMap = event.asTelemetry()
    if (event.didOriginateInDPS()) {
      synchronise {
        name = TIME_SLOT.entityName
        telemetryClient = this@VisitSlotsService.telemetryClient
        retryQueueService = officialVisitsRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getTimeSlotByDpsIdOrNull(event.additionalInformation.timeSlotId.toString())
        }
        transform {
          val dpsTimeSlot = dpsApiService.getTimeSlot(event.additionalInformation.timeSlotId).also {
            telemetryMap["dayOfWeek"] = it.dayCode.toString()
          }
          nomisApiService.createTimeSlot(event.additionalInformation.prisonId, dpsTimeSlot.dayCode.toDayOfWeekCreateVisitTimeSlot(), dpsTimeSlot.toCreateVisitTimeSlotRequest()).also {
            telemetryMap["nomisTimeSlotSequence"] = it.timeSlotSequence.toString()
          }.let {
            VisitTimeSlotMappingDto(
              dpsId = event.additionalInformation.timeSlotId.toString(),
              nomisSlotSequence = it.timeSlotSequence,
              nomisPrisonId = event.additionalInformation.prisonId,
              nomisDayOfWeek = it.dayOfWeek.toString(),
              mappingType = VisitTimeSlotMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createTimeSlotMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("${TIME_SLOT.entityName}-create-ignored", telemetryMap)
    }
  }
  suspend fun timeSlotUpdated(event: TimeSlotEvent) = telemetryClient.trackEvent("${TIME_SLOT.entityName}-update-success", event.asTelemetry())
  suspend fun timeSlotDeleted(event: TimeSlotEvent) = telemetryClient.trackEvent("${TIME_SLOT.entityName}-delete-success", event.asTelemetry())
  suspend fun visitSlotCreated(event: VisitSlotEvent) = telemetryClient.trackEvent("${VISIT_SLOT.entityName}-create-success", event.asTelemetry())
  suspend fun visitSlotUpdated(event: VisitSlotEvent) = telemetryClient.trackEvent("${VISIT_SLOT.entityName}-update-success", event.asTelemetry())
  suspend fun visitSlotDeleted(event: VisitSlotEvent) = telemetryClient.trackEvent("${VISIT_SLOT.entityName}-delete-success", event.asTelemetry())
  suspend fun createTimeSlotMapping(message: CreateMappingRetryMessage<VisitTimeSlotMappingDto>) = telemetryClient.trackEvent("${TIME_SLOT.entityName}-create-success", message.telemetryAttributes)
  suspend fun createVisitSlotMapping(message: CreateMappingRetryMessage<VisitSlotMappingDto>) = telemetryClient.trackEvent("${VISIT_SLOT.entityName}-create-success", message.telemetryAttributes)
}

fun TimeSlotEvent.asTelemetry() = mutableMapOf(
  "prisonId" to additionalInformation.prisonId,
  "dpsTimeSlotId" to additionalInformation.timeSlotId.toString(),
  "source" to additionalInformation.source,
)

fun VisitSlotEvent.asTelemetry() = mutableMapOf(
  "prisonId" to additionalInformation.prisonId,
  "dpsVisitSlotId" to additionalInformation.visitSlotId.toString(),
  "source" to additionalInformation.source,
)

private fun SourcedEvent.didOriginateInDPS() = this.additionalInformation.source == "DPS"
private fun DayType.toDayOfWeekCreateVisitTimeSlot(): DayOfWeekCreateVisitTimeSlot = when (this) {
  MON -> DayOfWeekCreateVisitTimeSlot.MON
  TUE -> DayOfWeekCreateVisitTimeSlot.TUE
  WED -> DayOfWeekCreateVisitTimeSlot.WED
  THU -> DayOfWeekCreateVisitTimeSlot.THU
  FRI -> DayOfWeekCreateVisitTimeSlot.FRI
  SAT -> DayOfWeekCreateVisitTimeSlot.SAT
  SUN -> DayOfWeekCreateVisitTimeSlot.SUN
}

private fun SyncTimeSlot.toCreateVisitTimeSlotRequest(): CreateVisitTimeSlotRequest = CreateVisitTimeSlotRequest(
  startTime = startTime,
  endTime = endTime,
  effectiveDate = effectiveDate,
  expiryDate = expiryDate,
)
