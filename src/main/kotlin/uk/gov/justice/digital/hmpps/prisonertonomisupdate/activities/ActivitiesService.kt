package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Activity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityPay
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityScheduleSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAllocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PayRateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ScheduleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ScheduleRuleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateAllocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.lang.Integer.min
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class ActivitiesService(
  private val activitiesApiService: ActivitiesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: ActivitiesMappingService,
  private val activitiesUpdateQueueService: ActivitiesUpdateQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  suspend fun createActivity(event: ScheduleDomainEvent) {
    synchronise {
      name = "activity"
      telemetryClient = this@ActivitiesService.telemetryClient
      retryQueueService = activitiesUpdateQueueService
      eventTelemetry = mapOf(
        "activityScheduleId" to event.additionalInformation.activityScheduleId.toString(),
      )

      checkMappingDoesNotExist {
        mappingService.getMappingGivenActivityScheduleIdOrNull(event.additionalInformation.activityScheduleId)
      }
      transform {
        activitiesApiService.getActivitySchedule(event.additionalInformation.activityScheduleId).let { activitySchedule ->
          eventTelemetry += "description" to activitySchedule.description

          createTransformedActivity(activitySchedule).let { nomisResponse ->
            ActivityMappingDto(
              nomisCourseActivityId = nomisResponse.courseActivityId,
              activityScheduleId = event.additionalInformation.activityScheduleId,
              mappingType = "ACTIVITY_CREATED",
            )
          }
        }
      }
      saveMapping { mappingService.createMapping(it) }
    }
  }

  private suspend fun createTransformedActivity(activitySchedule: ActivitySchedule) =
    activitiesApiService.getActivity(activitySchedule.activity.id).let {
      nomisApiService.createActivity(toNomisActivity(activitySchedule, it))
    }

  suspend fun updateActivity(event: ScheduleDomainEvent) {
    val activityScheduleId = event.additionalInformation.activityScheduleId
    val telemetryMap = mutableMapOf("activityScheduleId" to activityScheduleId.toString())

    runCatching {
      val nomisCourseActivityId = mappingService.getMappingGivenActivityScheduleId(activityScheduleId).nomisCourseActivityId
        .also { telemetryMap["nomisCourseActivityId"] = it.toString() }

      val activitySchedule = activitiesApiService.getActivitySchedule(activityScheduleId)

      val activity = activitiesApiService.getActivity(activitySchedule.activity.id)
        .also { telemetryMap["activityId"] = it.id.toString() }

      activitySchedule.toUpdateActivityRequest(activity.pay)
        .also { nomisApiService.updateActivity(nomisCourseActivityId, it) }
    }.onSuccess {
      telemetryClient.trackEvent("activity-amend-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-amend-failed", telemetryMap, null)
      throw e
    }
  }

  private fun ActivitySchedule.toUpdateActivityRequest(pay: List<ActivityPay>) =
    UpdateActivityRequest(
      endDate = endDate,
      internalLocationId = internalLocation?.id?.toLong(),
      payRates = pay.toPayRateRequests(),
      scheduleRules = slots.toScheduleRuleRequests(),
    )

  suspend fun updateScheduleInstances(amendInstancesEvent: ScheduleDomainEvent) {
    val telemetryMap = mutableMapOf("activityScheduleId" to amendInstancesEvent.additionalInformation.activityScheduleId.toString())

    runCatching {
      val activitySchedule = activitiesApiService.getActivitySchedule(amendInstancesEvent.additionalInformation.activityScheduleId)

      val nomisCourseActivityId = mappingService.getMappingGivenActivityScheduleId(activitySchedule.id).nomisCourseActivityId
        .also { telemetryMap["nomisCourseActivityId"] = it.toString() }

      activitySchedule.instances.toScheduleRequests()
        .also { nomisApiService.updateScheduleInstances(nomisCourseActivityId, it) }
    }.onSuccess {
      telemetryClient.trackEvent("schedule-instances-amend-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("schedule-instances-amend-failed", telemetryMap, null)
      throw e
    }
  }

  private fun List<ScheduledInstance>.toScheduleRequests() =
    map {
      ScheduleRequest(
        date = it.date,
        startTime = LocalTime.parse(it.startTime),
        endTime = LocalTime.parse(it.endTime),
      )
    }

  suspend fun createAllocation(allocationEvent: AllocationDomainEvent) {
    val telemetryMap = mutableMapOf(
      "allocationId" to allocationEvent.additionalInformation.allocationId.toString(),
    )
    runCatching {
      activitiesApiService.getAllocation(allocationEvent.additionalInformation.allocationId).let { allocation ->
        telemetryMap["offenderNo"] = allocation.prisonerNumber
        telemetryMap["bookingId"] = allocation.bookingId.toString()
        mappingService.getMappingGivenActivityScheduleId(allocation.scheduleId).let { mapping ->
          nomisApiService.createAllocation(
            mapping.nomisCourseActivityId,
            CreateAllocationRequest(
              bookingId = allocation.bookingId!!,
              startDate = allocation.startDate,
              endDate = allocation.endDate,
              payBandCode = allocation.prisonPayBand.nomisPayBand.toString(),
            ),
          ).also {
            telemetryMap["offenderProgramReferenceId"] = it.offenderProgramReferenceId.toString()
          }
        }
      }
    }.onSuccess {
      telemetryClient.trackEvent("activity-allocation-create-success", telemetryMap)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-allocation-create-failed", telemetryMap)
      throw e
    }
  }

  suspend fun deallocate(allocationEvent: AllocationDomainEvent) {
    val telemetryMap = mutableMapOf(
      "allocationId" to allocationEvent.additionalInformation.allocationId.toString(),
    )

    runCatching {
      activitiesApiService.getAllocation(allocationEvent.additionalInformation.allocationId).let { allocation ->
        telemetryMap["offenderNo"] = allocation.prisonerNumber
        telemetryMap["bookingId"] = allocation.bookingId.toString()
        mappingService.getMappingGivenActivityScheduleId(allocation.scheduleId)
          .let { mapping ->
            nomisApiService.deallocate(
              mapping.nomisCourseActivityId,
              UpdateAllocationRequest(
                bookingId = allocation.bookingId!!,
                endDate = allocation.endDate!!,
                endReason = allocation.deallocatedReason, // TODO SDIT-421 probably will need a mapping
                // endComment = allocation.?, // TODO SDIT-421 could put something useful in here
              ),
            ).also {
              telemetryMap["offenderProgramReferenceId"] = it.offenderProgramReferenceId.toString()
            }
          }
      }
    }.onSuccess {
      telemetryClient.trackEvent("activity-deallocate-success", telemetryMap)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-deallocate-failed", telemetryMap)
      throw e
    }
  }

  private fun toNomisActivity(schedule: ActivitySchedule, activity: Activity): CreateActivityRequest {
    return CreateActivityRequest(
      code = schedule.id.toString(),
      startDate = activity.startDate,
      endDate = activity.endDate,
      prisonId = activity.prisonCode,
      internalLocationId = schedule.internalLocation?.id?.toLong(),
      capacity = schedule.capacity,
      payRates = activity.pay.toPayRateRequests(),
      description = toNomisActivityDescription(activity.summary),
      minimumIncentiveLevelCode = activity.minimumIncentiveNomisCode,
      programCode = activity.category.code,
      payPerSession = activity.payPerSession.value,
      schedules = schedule.instances.toScheduleRequests(),
      scheduleRules = schedule.slots.toScheduleRuleRequests(),
    )
  }

  private fun toNomisActivityDescription(activityDescription: String): String =
    "SAA " + activityDescription.substring(0, min(36, activityDescription.length))

  private fun List<ActivityScheduleSlot>.toScheduleRuleRequests(): List<ScheduleRuleRequest> =
    map { slot ->
      ScheduleRuleRequest(
        startTime = LocalTime.parse(slot.startTime),
        endTime = LocalTime.parse(slot.endTime),
        monday = slot.mondayFlag,
        tuesday = slot.tuesdayFlag,
        wednesday = slot.wednesdayFlag,
        thursday = slot.thursdayFlag,
        friday = slot.fridayFlag,
        saturday = slot.saturdayFlag,
        sunday = slot.sundayFlag,
      )
    }

  private fun List<ActivityPay>.toPayRateRequests(): List<PayRateRequest> =
    map { p ->
      PayRateRequest(
        incentiveLevel = p.incentiveNomisCode,
        payBand = p.prisonPayBand.nomisPayBand.toString(),
        rate = BigDecimal(p.rate!!).movePointLeft(2),
      )
    }

  suspend fun createRetry(context: CreateMappingRetryMessage<ActivityContext>) {
    mappingService.createMapping(
      ActivityMappingDto(
        nomisCourseActivityId = context.mapping.nomisCourseActivityId,
        activityScheduleId = context.mapping.activityScheduleId,
        mappingType = "ACTIVITY_CREATED",
      ),
    ).also {
      telemetryClient.trackEvent(
        "activity-create-mapping-retry-success",
        mapOf("activityScheduleId" to context.mapping.activityScheduleId.toString()),
      )
    }
  }

  override suspend fun retryCreateMapping(message: String) = createRetry(message.fromJson())
  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class ScheduleDomainEvent(
  val eventType: String,
  val additionalInformation: ScheduleAdditionalInformation,
  val version: String,
  val description: String,
  val occurredAt: LocalDateTime,
)

data class ScheduleAdditionalInformation(
  val activityScheduleId: Long,
)

data class AllocationDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val occurredAt: LocalDateTime,
  val additionalInformation: AllocationAdditionalInformation,
)

data class AllocationAdditionalInformation(
  val allocationId: Long,
)
