package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import org.springframework.stereotype.Service
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
          startTime = LocalDateTime.of(this.visitDate, this.startTime),
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

  fun cancelVisit() {
  }

  fun updateVisit() {
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
}

data class VisitInformation(
  val visitId: String,
)
