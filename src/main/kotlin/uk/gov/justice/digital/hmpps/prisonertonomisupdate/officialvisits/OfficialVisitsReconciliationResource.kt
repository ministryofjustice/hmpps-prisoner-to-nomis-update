package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Official Visits Reconciliation Resource")
class OfficialVisitsReconciliationResource(
  private val reconciliationService: OfficialVisitsAllReconciliationService,
) {

  @PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
  @GetMapping("/official-visits/nomis-visit-id/{nomisVisitId}/reconciliation")
  suspend fun getOfficialVisitsReconciliationByNomisId(@PathVariable nomisVisitId: Long): MismatchVisit? = reconciliationService.checkVisitsMatch(nomisVisitId)
}
