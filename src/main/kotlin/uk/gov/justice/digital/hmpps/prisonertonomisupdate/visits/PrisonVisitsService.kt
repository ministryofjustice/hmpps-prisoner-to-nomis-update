package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CancelVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Service
class PrisonVisitsService(
  private val visitsApiService: VisitsApiService,
  private val nomisApiService: NomisApiService
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
          visitRoomId = this.visitRoom
        )
      ).run {
        visitsApiService.addVisitMapping(visitBookedEvent.visitId, this.visitId)
      }
    }
  }

  fun cancelVisit(visitCancelledEvent: VisitCancelledEvent) {
    nomisApiService.cancelVisit(
      CancelVisitDto(
        offenderNo = visitCancelledEvent.prisonerId,
        nomisVisitId = visitCancelledEvent.visitId
      )
    )
  }
}

data class VisitBookedEvent(
  val additionalInformation: VisitInformation,
  val occurredAt: OffsetDateTime,
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
    get() = additionalInformation.NOMISvisitId

  data class VisitInformation(
    val NOMISvisitId: String,
    val visitType: String?
  )
}
