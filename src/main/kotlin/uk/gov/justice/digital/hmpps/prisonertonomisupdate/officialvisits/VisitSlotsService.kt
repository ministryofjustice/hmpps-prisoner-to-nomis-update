package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.VisitsConfigurationResourceApi.DayOfWeekCreateVisitSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.VisitsConfigurationResourceApi.DayOfWeekCreateVisitTimeSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.VisitsConfigurationResourceApi.DayOfWeekDeleteVisitTimeSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.VisitsConfigurationResourceApi.DayOfWeekUpdateVisitTimeSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitSlotRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateVisitSlotRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateVisitTimeSlotRequest
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncVisitSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.track

@Service
class VisitSlotsService(
  override val telemetryClient: TelemetryClient,
  private val mappingApiService: VisitSlotsMappingService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  private val nomisApiService: VisitSlotsNomisApiService,
  private val officialVisitsRetryQueueService: OfficialVisitsRetryQueueService,
) : TelemetryEnabled {

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
  suspend fun timeSlotUpdated(event: TimeSlotEvent) {
    val telemetry = event.asTelemetry()
    if (event.didOriginateInDPS()) {
      track("${TIME_SLOT.entityName}-update", telemetry) {
        mappingApiService.getTimeSlotByDpsId(event.additionalInformation.timeSlotId.toString()).also { mapping ->
          telemetry["nomisSlotSequence"] = mapping.nomisSlotSequence.toString()
          telemetry["nomisDayOfWeek"] = mapping.nomisDayOfWeek
          telemetry["nomisPrisonId"] = mapping.nomisPrisonId
          val dpsTimeSlot = dpsApiService.getTimeSlot(event.additionalInformation.timeSlotId)
          nomisApiService.updateTimeSlot(
            mapping.nomisPrisonId,
            mapping.nomisDayOfWeek.toDayOfWeekUpdateVisitTimeSlot(),
            mapping.nomisSlotSequence,
            dpsTimeSlot.toUpdateVisitTimeSlotRequest(),
          )
        }
      }
    } else {
      telemetryClient.trackEvent("${TIME_SLOT.entityName}-update-ignored", telemetry)
    }
  }
  suspend fun timeSlotDeleted(event: TimeSlotEvent) {
    val telemetry = event.asTelemetry()
    mappingApiService.getTimeSlotByDpsIdOrNull(event.additionalInformation.timeSlotId.toString())?.also { mapping ->
      telemetry["nomisSlotSequence"] = mapping.nomisSlotSequence.toString()
      telemetry["nomisDayOfWeek"] = mapping.nomisDayOfWeek
      telemetry["nomisPrisonId"] = mapping.nomisPrisonId
      track("${TIME_SLOT.entityName}-delete", telemetry) {
        nomisApiService.deleteTimeSlot(
          prisonId = mapping.nomisPrisonId,
          dayOfWeek = mapping.nomisDayOfWeek.toDayOfWeekDeleteVisitTimeSlot(),
          timeSlotSequence = mapping.nomisSlotSequence,
        )
        mappingApiService.deleteTimeSlotByNomisIds(
          nomisPrisonId = mapping.nomisPrisonId,
          nomisDayOfWeek = mapping.nomisDayOfWeek,
          nomisSlotSequence = mapping.nomisSlotSequence,
        )
      }
    } ?: run {
      telemetryClient.trackEvent("${TIME_SLOT.entityName}-delete-skipped", telemetry)
    }
  }
  suspend fun visitSlotCreated(event: VisitSlotEvent) {
    val telemetryMap = event.asTelemetry()
    if (event.didOriginateInDPS()) {
      synchronise {
        name = VISIT_SLOT.entityName
        telemetryClient = this@VisitSlotsService.telemetryClient
        retryQueueService = officialVisitsRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getVisitSlotByDpsIdOrNull(event.additionalInformation.visitSlotId.toString())
        }
        transform {
          val dpsVisitSlot = dpsApiService.getVisitSlot(event.additionalInformation.visitSlotId).also {
            telemetryMap["dpsTimeSlotId"] = it.prisonTimeSlotId.toString()
          }
          val timeSlotMapping = mappingApiService.getTimeSlotByDpsId(dpsVisitSlot.prisonTimeSlotId.toString()).also {
            telemetryMap["nomisDayOfWeek"] = it.nomisDayOfWeek
            telemetryMap["nomisTimeSlotSequence"] = it.nomisSlotSequence.toString()
          }
          val locationMapping = mappingApiService.getInternalLocationByDpsId(dpsVisitSlot.dpsLocationId.toString()).also {
            telemetryMap["nomisLocationId"] = it.nomisLocationId.toString()
          }
          nomisApiService.createVisitSlot(
            prisonId = timeSlotMapping.nomisPrisonId,
            dayOfWeek = timeSlotMapping.nomisDayOfWeek.toDayOfWeekCreateVisitSlot(),
            timeSlotSequence = timeSlotMapping.nomisSlotSequence,
            request = dpsVisitSlot.toCreateVisitSlotRequest(locationMapping.nomisLocationId),
          ).also {
            telemetryMap["nomisVisitSlotId"] = it.id.toString()
          }.let {
            VisitSlotMappingDto(
              dpsId = event.additionalInformation.visitSlotId.toString(),
              nomisId = it.id,
              mappingType = VisitSlotMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createVisitSlotMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("${VISIT_SLOT.entityName}-create-ignored", telemetryMap)
    }
  }
  suspend fun visitSlotUpdated(event: VisitSlotEvent) {
    val telemetry = event.asTelemetry()
    if (event.didOriginateInDPS()) {
      track("${VISIT_SLOT.entityName}-update", telemetry) {
        mappingApiService.getVisitSlotByDpsId(event.additionalInformation.visitSlotId.toString()).also { mapping ->
          telemetry["nomisVisitSlotId"] = mapping.nomisId.toString()

          val dpsVisitSlot = dpsApiService.getVisitSlot(event.additionalInformation.visitSlotId)
          val locationMapping = mappingApiService.getInternalLocationByDpsId(dpsVisitSlot.dpsLocationId.toString()).also {
            telemetry["nomisLocationId"] = it.nomisLocationId.toString()
          }
          nomisApiService.updateVisitSlot(visitSlotId = mapping.nomisId, dpsVisitSlot.toUpdateVisitSlotRequest(locationMapping.nomisLocationId))
        }
      }
    } else {
      telemetryClient.trackEvent("${VISIT_SLOT.entityName}-update-ignored", telemetry)
    }
  }

  suspend fun visitSlotDeleted(event: VisitSlotEvent) {
    val telemetry = event.asTelemetry()
    mappingApiService.getVisitSlotByDpsIdOrNull(event.additionalInformation.visitSlotId.toString())?.also { mapping ->
      telemetry["nomisVisitSlotId"] = mapping.nomisId.toString()
      track("${VISIT_SLOT.entityName}-delete", telemetry) {
        nomisApiService.deleteVisitSlot(mapping.nomisId)
        mappingApiService.deleteVisitSlotByNomisId(mapping.nomisId)
      }
    } ?: run {
      telemetryClient.trackEvent("${VISIT_SLOT.entityName}-delete-skipped", telemetry)
    }
  }
  suspend fun createTimeSlotMapping(message: CreateMappingRetryMessage<VisitTimeSlotMappingDto>) {
    mappingApiService.createTimeSlotMapping(message.mapping)
    telemetryClient.trackEvent("${TIME_SLOT.entityName}-create-success", message.telemetryAttributes)
  }
  suspend fun createVisitSlotMapping(message: CreateMappingRetryMessage<VisitSlotMappingDto>) {
    mappingApiService.createVisitSlotMapping(message.mapping)
    telemetryClient.trackEvent("${VISIT_SLOT.entityName}-create-success", message.telemetryAttributes)
  }
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
private fun String.toDayOfWeekCreateVisitSlot(): DayOfWeekCreateVisitSlot = when (this) {
  "MON" -> DayOfWeekCreateVisitSlot.MON
  "TUE" -> DayOfWeekCreateVisitSlot.TUE
  "WED" -> DayOfWeekCreateVisitSlot.WED
  "THU" -> DayOfWeekCreateVisitSlot.THU
  "FRI" -> DayOfWeekCreateVisitSlot.FRI
  "SAT" -> DayOfWeekCreateVisitSlot.SAT
  "SUN" -> DayOfWeekCreateVisitSlot.SUN
  else -> throw IllegalArgumentException("Unknown day of week: $this")
}
private fun String.toDayOfWeekUpdateVisitTimeSlot(): DayOfWeekUpdateVisitTimeSlot = when (this) {
  "MON" -> DayOfWeekUpdateVisitTimeSlot.MON
  "TUE" -> DayOfWeekUpdateVisitTimeSlot.TUE
  "WED" -> DayOfWeekUpdateVisitTimeSlot.WED
  "THU" -> DayOfWeekUpdateVisitTimeSlot.THU
  "FRI" -> DayOfWeekUpdateVisitTimeSlot.FRI
  "SAT" -> DayOfWeekUpdateVisitTimeSlot.SAT
  "SUN" -> DayOfWeekUpdateVisitTimeSlot.SUN
  else -> throw IllegalArgumentException("Unknown day of week: $this")
}

private fun String.toDayOfWeekDeleteVisitTimeSlot(): DayOfWeekDeleteVisitTimeSlot = when (this) {
  "MON" -> DayOfWeekDeleteVisitTimeSlot.MON
  "TUE" -> DayOfWeekDeleteVisitTimeSlot.TUE
  "WED" -> DayOfWeekDeleteVisitTimeSlot.WED
  "THU" -> DayOfWeekDeleteVisitTimeSlot.THU
  "FRI" -> DayOfWeekDeleteVisitTimeSlot.FRI
  "SAT" -> DayOfWeekDeleteVisitTimeSlot.SAT
  "SUN" -> DayOfWeekDeleteVisitTimeSlot.SUN
  else -> throw IllegalArgumentException("Unknown day of week: $this")
}

private fun SyncTimeSlot.toCreateVisitTimeSlotRequest(): CreateVisitTimeSlotRequest = CreateVisitTimeSlotRequest(
  startTime = startTime,
  endTime = endTime,
  effectiveDate = effectiveDate,
  expiryDate = expiryDate,
)

private fun SyncTimeSlot.toUpdateVisitTimeSlotRequest(): UpdateVisitTimeSlotRequest = UpdateVisitTimeSlotRequest(
  startTime = startTime,
  endTime = endTime,
  effectiveDate = effectiveDate,
  expiryDate = expiryDate,
)

private fun SyncVisitSlot.toCreateVisitSlotRequest(nomisLocationId: Long): CreateVisitSlotRequest = CreateVisitSlotRequest(
  maxAdults = maxAdults,
  maxGroups = maxGroups,
  internalLocationId = nomisLocationId,
)

private fun SyncVisitSlot.toUpdateVisitSlotRequest(nomisLocationId: Long): UpdateVisitSlotRequest = UpdateVisitSlotRequest(
  maxAdults = maxAdults,
  maxGroups = maxGroups,
  internalLocationId = nomisLocationId,
)
