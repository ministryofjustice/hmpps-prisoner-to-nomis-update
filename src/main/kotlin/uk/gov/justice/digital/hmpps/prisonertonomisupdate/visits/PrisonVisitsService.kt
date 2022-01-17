package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import org.springframework.stereotype.Service

@Service
class PrisonVisitsService(
  private val visitsApiService: VisitsApiService,
  private val nomisApiService: NomisApiService
) {

  fun createVisit(visitId: String) {
    visitsApiService.getVisit(visitId)?.run {
      nomisApiService.createVisit(CreateVisitDto(offenderNo = this.prisonerId))
    }
  }

  fun cancelVisit() {
  }

  fun updateVisit() {
  }
}
