package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.AttendanceType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitCompletionType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitStatusType
import java.time.LocalDateTime
import java.time.LocalTime

data class OfficialVisitSummary(
  @Schema(description = "The start time of the visit", example = "2021-07-16T12:34:56")
  val startDateTime: LocalDateTime,
  @Schema(description = "The end time of the visit", example = "2020-04-20T18:00:00")
  val endDateTime: LocalDateTime,
  val prisonId: String,
  val offenderNo: String,
  val visitStatus: VisitStatusType,
  val visitOutcome: VisitCompletionType?,
  val visitors: List<VisitorSummary>,
)
data class VisitorSummary(val nomisPersonAndDpsContactId: Long, val attendance: AttendanceType?)

internal fun OfficialVisitResponse.toVisit() = OfficialVisitSummary(
  startDateTime = this.startDateTime,
  endDateTime = this.endDateTime,
  prisonId = this.prisonId,
  offenderNo = this.offenderNo,
  visitStatus = this.visitStatus.code.toDpsVisitStatusType(),
  visitOutcome = this.cancellationReason?.code.toDpsVisitCompletionType(this.visitStatus.code),
  visitors = this.visitors.map {
    VisitorSummary(
      nomisPersonAndDpsContactId = it.personId,
      attendance = it.visitorAttendanceOutcome?.code?.toDpsAttendanceType(),
    )
  }.sortedBy { it.nomisPersonAndDpsContactId },
)
internal fun SyncOfficialVisit.toVisit() = OfficialVisitSummary(
  startDateTime = this.visitDate.atTime(this.startTime.asTime()),
  endDateTime = this.visitDate.atTime(this.endTime.asTime()),
  prisonId = this.prisonCode,
  offenderNo = this.prisonerNumber,
  visitStatus = this.statusCode,
  visitOutcome = this.completionCode,
  visitors = this.visitors.filter { it.contactId != null }.map {
    VisitorSummary(
      nomisPersonAndDpsContactId = it.contactId!!,
      attendance = it.attendanceCode,
    )
  }.sortedBy { it.nomisPersonAndDpsContactId },
)

private fun String.asTime() = LocalTime.parse(this)

private fun String.toDpsVisitStatusType(): VisitStatusType = when (this) {
  "CANC" -> VisitStatusType.CANCELLED
  "VDE" -> VisitStatusType.COMPLETED
  "HMPOP" -> VisitStatusType.COMPLETED
  "OFFEND" -> VisitStatusType.COMPLETED
  "VISITOR" -> VisitStatusType.COMPLETED
  "NORM" -> VisitStatusType.COMPLETED
  "SCH" -> VisitStatusType.SCHEDULED
  "EXP" -> VisitStatusType.EXPIRED
  else -> throw IllegalArgumentException("Unknown visit status code: $this")
}

private fun String?.toDpsVisitCompletionType(nomisVisitStatus: String): VisitCompletionType? = when (this) {
  "NO_VO" -> VisitCompletionType.STAFF_CANCELLED

  "VO_CANCEL" -> VisitCompletionType.STAFF_CANCELLED

  "REFUSED" -> VisitCompletionType.PRISONER_REFUSED

  "OFFCANC" -> VisitCompletionType.PRISONER_CANCELLED

  "VISCANC" -> VisitCompletionType.VISITOR_CANCELLED

  "NSHOW" -> VisitCompletionType.VISITOR_NO_SHOW

  "ADMIN" -> VisitCompletionType.STAFF_CANCELLED

  "ADMIN_CANCEL" -> VisitCompletionType.STAFF_CANCELLED

  "HMP" -> VisitCompletionType.STAFF_CANCELLED

  "NO_ID" -> VisitCompletionType.VISITOR_DENIED

  "BATCH_CANC" -> VisitCompletionType.STAFF_CANCELLED

  null -> when (nomisVisitStatus) {
    "VDE" -> VisitCompletionType.VISITOR_DENIED
    "HMPOP" -> VisitCompletionType.STAFF_EARLY
    "OFFEND" -> VisitCompletionType.PRISONER_EARLY
    "VISITOR" -> VisitCompletionType.VISITOR_EARLY
    "NORM" -> VisitCompletionType.NORMAL
    "SCH" -> null
    "EXP" -> VisitCompletionType.NORMAL
    else -> null
  }

  else -> null
}

private fun String.toDpsAttendanceType(): AttendanceType = when (this) {
  "ATT" -> AttendanceType.ATTENDED
  "ABS" -> AttendanceType.ABSENT
  else -> throw IllegalArgumentException("Unknown attendance type code: $this")
}
