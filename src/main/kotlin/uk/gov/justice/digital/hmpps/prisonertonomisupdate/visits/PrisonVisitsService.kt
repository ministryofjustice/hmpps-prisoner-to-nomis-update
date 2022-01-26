package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CancelVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class PrisonVisitsService(
  private val visitsApiService: VisitsApiService,
  private val nomisApiService: NomisApiService,
  private val telemetryClient: TelemetryClient
) {

  fun createVisit(visitBookedEvent: VisitBookedEvent) {
    visitsApiService.getVisit(visitBookedEvent.visitId).run {
      nomisApiService.createVisit(
        CreateVisitDto(
          offenderNo = this.prisonerId,
          prisonId = this.prisonId,
          startDateTime = LocalDateTime.of(this.visitDate, this.startTime),
          endTime = this.endTime,
          visitorPersonIds = this.visitors.map { it -> it.nomisPersonId },
          issueDate = visitBookedEvent.bookingDate,
          visitType = "SCON", // TODO mapping
          // visitRoomId = this.visitRoom,
          vsipVisitId = this.visitId
        )
      )
      val telemetryProperties = mapOf(
        "offenderNo" to this.prisonerId,
        "prisonId" to this.prisonId,
        "visitId" to this.visitId,
        "startDateTime" to LocalDateTime.of(this.visitDate, this.startTime).format(DateTimeFormatter.ISO_DATE_TIME),
        "endTime" to this.endTime.format(DateTimeFormatter.ISO_TIME),
      )
      telemetryClient.trackEvent("visit-booked-event", telemetryProperties, null)
    }
  }

  fun cancelVisit(visitCancelledEvent: VisitCancelledEvent) {
    nomisApiService.cancelVisit(
      CancelVisitDto(
        offenderNo = visitCancelledEvent.prisonerId,
        visitId = visitCancelledEvent.visitId,
        outcome = "VISCANC" // TODO mapping
      )
    )
    val telemetryProperties = mapOf(
      "offenderNo" to visitCancelledEvent.prisonerId,
      "visitId" to visitCancelledEvent.visitId
    )
    telemetryClient.trackEvent("visit-cancelled-event", telemetryProperties, null)
  }
}

data class VisitBookedEvent(
  val additionalInformation: VisitInformation,
  val occurredAt: OffsetDateTime,
  val prisonerId: String,
) {
  val bookingDate: LocalDate
    get() = occurredAt.toLocalDate()

  val visitId: String
    get() = additionalInformation.visitId

  data class VisitInformation(
    val visitId: String
  )
}

data class VisitCancelledEvent(
  val additionalInformation: VisitInformation,
  val occurredAt: OffsetDateTime,
  val prisonerId: String,
) {

  val visitId: String
    get() = additionalInformation.visitId

  data class VisitInformation(
    val visitId: String,
  )
}
