package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime

@Service
class PrisonVisitsService(
  private val visitsApiService: VisitsApiService,
  private val nomisApiService: NomisApiService
) {

  fun createVisit(visitId: String, bookingDate: LocalDateTime) {
    visitsApiService.getVisit(visitId).run {
      nomisApiService.createVisit(
        CreateVisitDto(
          offenderNo = this.prisonerId,
          prisonId = this.prisonId,
          startTime = LocalDateTime.of(this.visitDate, this.startTime),
          endTime = this.endTime,
          visitorPersonIds = this.visitors.map { it -> it.nomisPersonId },
          issueDate = bookingDate.toLocalDate(),
          visitType = "SCON", // TODO mapping
          visitRoomId = this.visitRoom
        )
      ).run {
        visitsApiService.updateVisitMapping(visitId, this.visitId)
      }
    }
  }

  fun cancelVisit() {
  }

  fun updateVisit() {
  }
}
