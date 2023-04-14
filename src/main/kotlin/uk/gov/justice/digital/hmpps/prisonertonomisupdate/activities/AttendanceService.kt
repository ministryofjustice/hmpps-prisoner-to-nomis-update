package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AttendanceSync
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.GetAttendanceStatusRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpsertAttendanceRequest
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class AttendanceService(
  private val activitiesApiService: ActivitiesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: ActivitiesMappingService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun upsertAttendance(attendanceEvent: AttendanceDomainEvent) {
    val attendanceId = attendanceEvent.additionalInformation.attendanceId
    val telemetryMap = mutableMapOf("attendanceId" to attendanceId.toString())
    val upsertType = if (attendanceEvent.eventType.contains("created")) "create" else "update"

    runCatching {
      val attendanceSync = activitiesApiService.getAttendanceSync(attendanceId)
        .also { telemetryMap.putAll(it.toTelemetry()) }

      val nomisCourseActivityId = mappingService.getMappingGivenActivityScheduleId(attendanceSync.activityScheduleId).nomisCourseActivityId
        .also { telemetryMap["nomisCourseActivityId"] = it.toString() }

      val nomisAttendanceStatus = if (attendanceSync.status == "LOCKED") {
        getNomisAttendanceStatus(nomisCourseActivityId, attendanceSync)?.eventStatus
      } else {
        null
      }

      nomisApiService.upsertAttendance(
        nomisCourseActivityId,
        attendanceSync.bookingId,
        attendanceSync.toUpsertAttendanceRequest(nomisAttendanceStatus),
      ).also {
        telemetryMap["attendanceEventId"] = it.eventId.toString()
        telemetryMap["nomisCourseScheduleId"] = it.courseScheduleId.toString()
        telemetryMap["created"] = it.created.toString()
      }
    }.onSuccess {
      telemetryClient.trackEvent("activity-attendance-$upsertType-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-attendance-$upsertType-failed", telemetryMap, null)
      throw e
    }
  }

  private suspend fun getNomisAttendanceStatus(
    nomisCourseActivityId: Long,
    attendanceSync: AttendanceSync,
  ) =
    nomisApiService.getAttendanceStatus(
      nomisCourseActivityId,
      attendanceSync.bookingId,
      GetAttendanceStatusRequest(
        attendanceSync.sessionDate,
        LocalTime.parse(attendanceSync.sessionStartTime),
        LocalTime.parse(attendanceSync.sessionEndTime),
      ),
    )

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

  private suspend fun AttendanceSync.toUpsertAttendanceRequest(nomisAttendanceStatus: String?): UpsertAttendanceRequest {
    val eventOutcome = toEventOutcome()
    return UpsertAttendanceRequest(
      scheduleDate = this.sessionDate,
      startTime = LocalTime.parse(this.sessionStartTime),
      endTime = LocalTime.parse(this.sessionEndTime),
      eventStatusCode = toEventStatus(nomisAttendanceStatus),
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

    it == "NOT_REQUIRED" -> EventOutcome("NREQ", unexcusedAbsence = false, authorisedAbsence = true)

    it == "REST" && issuePayment == true -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "REST" && issuePayment != true -> EventOutcome("REST", unexcusedAbsence = false, authorisedAbsence = true)

    it == "CLASH" -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "OTHER" && issuePayment == true -> EventOutcome("ACCAB", unexcusedAbsence = false, authorisedAbsence = true)

    it == "OTHER" && issuePayment != true -> EventOutcome("UNACAB", unexcusedAbsence = true, authorisedAbsence = false)

    else -> throw InvalidAttendanceReasonException("Unable to handle attendance reason code=$it and paid=$issuePayment")
  }
}

fun AttendanceSync.toEventStatus(nomisAttendanceStatus: String?): String = status.let {
  when {
    it == "WAITING" -> "SCH"

    it == "COMPLETED" && attendanceReasonCode == "CANCELLED" -> "CANC"

    it == "COMPLETED" -> "COMP"

    it == "LOCKED" -> when (nomisAttendanceStatus) {
      "SCH" -> "EXP"
      null -> "EXP"
      else -> nomisAttendanceStatus
    }

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
