package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AttendanceSync
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.AttendancePaidException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerMovedAllocationEndedException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertAttendanceRequest
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class AttendanceService(
  private val activitiesApiService: ActivitiesApiService,
  private val activitiesNomisApiService: ActivitiesNomisApiService,
  private val mappingService: ActivitiesMappingService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun upsertAttendanceEvent(attendanceEvent: AttendanceDomainEvent) {
    upsertAttendance(attendanceEvent.additionalInformation.attendanceId, attendanceEvent.eventType.contains("created"))
  }

  suspend fun upsertAttendance(attendanceId: Long, createRequest: Boolean = false) {
    val telemetryMap = mutableMapOf("dpsAttendanceId" to attendanceId.toString())
    val upsertType = if (createRequest) "create" else "update"
    lateinit var attendanceSync: AttendanceSync

    runCatching {
      attendanceSync = activitiesApiService.getAttendanceSync(attendanceId)
        .also { telemetryMap.putAll(it.toTelemetry()) }

      val mappings = mappingService.getMappings(attendanceSync.activityScheduleId)
        .also { telemetryMap["nomisCourseActivityId"] = it.nomisCourseActivityId.toString() }
      val nomisCourseScheduleId = mappings.scheduledInstanceMappings
        .find { it.scheduledInstanceId == attendanceSync.scheduledInstanceId }
        ?.nomisCourseScheduleId
        ?.also { telemetryMap["nomisCourseScheduleId"] = it.toString() }
        ?: throw ValidationException("Mapping for Activity's scheduled instance id not found: ${attendanceSync.scheduledInstanceId}")

      activitiesNomisApiService.upsertAttendance(
        nomisCourseScheduleId,
        attendanceSync.bookingId,
        attendanceSync.toUpsertAttendanceRequest(),
      ).also {
        telemetryMap["nomisAttendanceEventId"] = it.eventId.toString()
        telemetryMap["created"] = it.created.toString()
        telemetryMap["prisonId"] = it.prisonId
      }
    }.onSuccess {
      telemetryClient.trackEvent("activity-attendance-$upsertType-success", telemetryMap, null)
    }.onFailure { e ->
      // Do not error if the prisoner was paid today - bespoke processes such as Discharge Balance Calculation that pay before the overnight payroll can be ignored
      if (e is AttendancePaidException && attendanceSync.sessionDate == LocalDate.now()) {
        telemetryMap["reason"] = "Attendance update ignored as already paid session on ${attendanceSync.sessionDate} at ${attendanceSync.sessionStartTime}"
        telemetryClient.trackEvent("activity-attendance-$upsertType-ignored", telemetryMap, null)
      } else if (e is PrisonerMovedAllocationEndedException) {
        telemetryMap["reason"] = "Attendance update ignored as the prisoner is deallocated and has moved from the prison"
        telemetryClient.trackEvent("activity-attendance-$upsertType-ignored", telemetryMap, null)
      } else {
        telemetryClient.trackEvent("activity-attendance-$upsertType-failed", telemetryMap, null)
        throw e
      }
    }
  }

  private fun AttendanceSync.toTelemetry(): Map<String, String> = mapOf(
    "dpsAttendanceId" to attendanceId.toString(),
    "dpsActivityScheduleId" to activityScheduleId.toString(),
    "dpsScheduleInstanceId" to scheduledInstanceId.toString(),
    "sessionDate" to sessionDate.toString(),
    "sessionStartTime" to sessionStartTime,
    "sessionEndTime" to sessionEndTime,
    "offenderNo" to prisonerNumber,
    "bookingId" to bookingId.toString(),
  )

  private suspend fun AttendanceSync.toUpsertAttendanceRequest(): UpsertAttendanceRequest {
    val eventOutcome = toEventOutcome()
    return UpsertAttendanceRequest(
      scheduleDate = this.sessionDate,
      startTime = this.sessionStartTime,
      endTime = this.sessionEndTime,
      eventStatusCode = toEventStatus(),
      eventOutcomeCode = eventOutcome?.code,
      comments = comment,
      unexcusedAbsence = eventOutcome?.unexcusedAbsence ?: false,
      authorisedAbsence = eventOutcome?.authorisedAbsence ?: false,
      paid = issuePayment ?: false,
      bonusPay = bonusAmount?.let { BigDecimal(it).setScale(3, RoundingMode.HALF_UP) },
    )
  }

  suspend fun deleteAttendanceEvent(attendanceEvent: DeleteAttendanceDomainEvent) {
    deleteAttendance(attendanceEvent.additionalInformation.scheduledInstanceId, attendanceEvent.additionalInformation.bookingId)
  }

  suspend fun deleteAttendance(scheduledInstanceId: Long, bookingId: Long) {
    val telemetryMap = mutableMapOf(
      "dpsScheduledInstanceId" to scheduledInstanceId.toString(),
      "bookingId" to bookingId.toString(),
    )

    runCatching {
      val mapping = mappingService.getScheduledInstanceMappingOrNull(scheduledInstanceId)
        ?: throw ValidationException("Mapping for Activity's scheduled instance id not found: $scheduledInstanceId}")
      telemetryMap["nomisCourseScheduleId"] = mapping.nomisCourseScheduleId.toString()

      activitiesNomisApiService.deleteAttendance(mapping.nomisCourseScheduleId, bookingId)
    }.onSuccess {
      telemetryClient.trackEvent("activity-attendance-delete-success", telemetryMap, null)
    }.onFailure { e ->
      when (e) {
        is WebClientResponseException.NotFound -> telemetryClient.trackEvent("activity-attendance-delete-ignored", telemetryMap, null)
        else -> {
          telemetryClient.trackEvent("activity-attendance-delete-failed", telemetryMap, null)
          throw e
        }
      }
    }
  }
}

data class EventOutcome(
  val code: String,
  val unexcusedAbsence: Boolean,
  val authorisedAbsence: Boolean,
)

fun AttendanceSync.toEventOutcome() = attendanceReasonCode?.let {
  when {
    it == "ATTENDED" -> EventOutcome("ATT", unexcusedAbsence = false, authorisedAbsence = false)

    it == "CANCELLED" -> EventOutcome("CANC", unexcusedAbsence = false, authorisedAbsence = true)

    it == "SUSPENDED" || it == "AUTO_SUSPENDED" -> EventOutcome("SUS", unexcusedAbsence = false, authorisedAbsence = true)

    it == "SICK" && issuePayment == true -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "SICK" && issuePayment != true -> EventOutcome("REST", unexcusedAbsence = false, authorisedAbsence = true)

    it == "REFUSED" -> EventOutcome("UNACAB", unexcusedAbsence = true, authorisedAbsence = false)

    it == "NOT_REQUIRED" -> EventOutcome("NREQ", unexcusedAbsence = false, authorisedAbsence = true)

    it == "REST" && issuePayment == true -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "REST" && issuePayment != true -> EventOutcome("REST", unexcusedAbsence = false, authorisedAbsence = true)

    it == "CLASH" -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "OTHER" && issuePayment == true -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "OTHER" && issuePayment != true -> EventOutcome("UNACAB", unexcusedAbsence = true, authorisedAbsence = false)

    else -> throw InvalidAttendanceReasonException("Unable to handle attendance reason code=$it and paid=$issuePayment")
  }
}

fun AttendanceSync.toEventStatus(): String = status.let {
  when {
    it == "WAITING" -> if (sessionDate >= LocalDate.now()) "SCH" else "EXP"

    it == "COMPLETED" && attendanceReasonCode == "CANCELLED" -> "CANC"

    it == "COMPLETED" -> "COMP"

    else -> throw InvalidAttendanceStatusException("Unable to handle attendance status code=$it and attendance reason code=$attendanceReasonCode")
  }
}

data class AttendanceDomainEvent(
  val eventType: String,
  val additionalInformation: AttendanceAdditionalInformation,
  val version: String,
  val description: String,
)

data class AttendanceAdditionalInformation(
  val attendanceId: Long,
)

data class DeleteAttendanceDomainEvent(
  val eventType: String,
  val additionalInformation: DeleteAttendanceAdditionalInformation,
  val version: String,
  val description: String,
)

data class DeleteAttendanceAdditionalInformation(
  val scheduledInstanceId: Long,
  val bookingId: Long,
)

class InvalidAttendanceReasonException(message: String) : IllegalStateException(message)
class InvalidAttendanceStatusException(message: String) : IllegalStateException(message)
