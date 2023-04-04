package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AttendanceSync
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAttendanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@Service
class AttendanceService(
  private val activitiesApiService: ActivitiesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: ActivitiesMappingService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun createAttendance(attendanceEvent: AttendanceDomainEvent) {
    val attendanceId = attendanceEvent.additionalInformation.attendanceId
    val telemetryMap = mutableMapOf("attendanceId" to attendanceId.toString())

    runCatching {
      val attendanceSync = activitiesApiService.getAttendanceSync(attendanceId)
        .also { telemetryMap.putAll(it.toTelemetry()) }

      val nomisCourseActivityId = mappingService.getMappingGivenActivityScheduleId(attendanceSync.activityScheduleId).nomisCourseActivityId
        .also { telemetryMap["nomisCourseActivityId"] = it.toString() }

      // TODO SDIT-688 If this call responds with a 409=conflict (i.e. attendance already exists) can/should we treat this as an update instead?
      nomisApiService.createAttendance(
        nomisCourseActivityId,
        attendanceSync.bookingId,
        attendanceSync.toNomisCourseAttendance(),
      ).also {
        telemetryMap["attendanceEventId"] = it.eventId.toString()
      }
    }.onSuccess {
      telemetryClient.trackEvent("activity-attendance-create-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-attendance-create-failed", telemetryMap, null)
      throw e
    }
  }

  private fun AttendanceSync.toTelemetry(): Map<String, String> =
    mapOf(
      "attendanceId" to attendanceId.toString(),
      "activityScheduleId" to activityScheduleId.toString(),
      "scheduleInstanceId" to scheduledInstanceId.toString(),
      "sessionDate" to sessionDate.toString(),
      "sessionStartTime" to sessionStartTime,
      "sessionEndTime" to sessionEndTime,
      "prisonerNumber" to prisonerNumber,
      "bookingId" to bookingId.toString(),
    )

  fun AttendanceSync.toNomisCourseAttendance(): CreateAttendanceRequest {
    val eventOutcome = toEventOutcome()
    return CreateAttendanceRequest(
      eventStatusCode = toEventStatus(),
      eventOutcomeCode = eventOutcome?.code,
      comments = comment,
      unexcusedAbsence = eventOutcome?.unexcusedAbsence ?: false,
      authorisedAbsence = eventOutcome?.authorisedAbsence ?: false,
      paid = issuePayment ?: false,
      bonusPay = bonusAmount?.let { BigDecimal(it).setScale(3, RoundingMode.HALF_UP) },
    )
  }
}

data class EventOutcome(
  val code: String,
  val unexcusedAbsence: Boolean,
  val authorisedAbsence: Boolean,
)

fun AttendanceSync.toEventOutcome() = attendanceReasonCode?.let {
  when {
    it == "ATTENDED" && issuePayment == true -> EventOutcome("ATT", unexcusedAbsence = false, authorisedAbsence = false)

    it == "ATTENDED" && issuePayment != true -> EventOutcome("UNBEH", unexcusedAbsence = false, authorisedAbsence = false)

    it == "CANCELLED" -> EventOutcome("CANC", unexcusedAbsence = false, authorisedAbsence = true)

    it == "SUSPENDED" -> EventOutcome("SUS", unexcusedAbsence = false, authorisedAbsence = true)

    it == "SICK" && issuePayment == true -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "SICK" && issuePayment != true -> EventOutcome("REST", unexcusedAbsence = false, authorisedAbsence = true)

    it == "REFUSED" -> EventOutcome("UNACAB", unexcusedAbsence = true, authorisedAbsence = false)

    it == "NOT_REQUIRED" -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "REST" && issuePayment == true -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "REST" && issuePayment != true -> EventOutcome("REST", unexcusedAbsence = false, authorisedAbsence = true)

    it == "CLASH" -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "OTHER" && issuePayment == true -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "OTHER" && issuePayment != true -> EventOutcome("UNACAB", unexcusedAbsence = true, authorisedAbsence = false)

    else -> throw InvalidAttendanceReasonException("Unable to handle attendance reason code=$it and paid=$issuePayment")
  }
}

fun AttendanceSync.toEventStatus() = status.let {
  when {
    it == "WAITING" -> "SCH"

    it == "COMPLETED" && attendanceReasonCode == "CANCELLED" -> "CANC"

    it == "COMPLETED" -> "COMP"

    // TODO SDIT-688 For updates, if the old Nomis event status is COMP then it does not change if now LOCKED
    it == "LOCKED" -> "EXP"

    else -> throw InvalidAttendanceStatusException("Unable to handle attendance status code=$it and attendance reason code=$attendanceReasonCode")
  }
}

data class AttendanceDomainEvent(
  val eventType: String,
  val additionalInformation: AttendanceAdditionalInformation,
  val version: String,
  val description: String,
  val occurredAt: LocalDateTime,
)

data class AttendanceAdditionalInformation(
  val attendanceId: Long,
)

class InvalidAttendanceReasonException(message: String) : IllegalStateException(message)
class InvalidAttendanceStatusException(message: String) : IllegalStateException(message)
