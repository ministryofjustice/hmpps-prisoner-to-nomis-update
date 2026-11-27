package uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
@RestController
@Tag(name = "Calculate Release Dates Reconciliation Resource")
class CalculateReleaseDatesReconciliationResource(
  private val reconciliationService: CalculateReleaseDatesReconciliationService,
) {
  @GetMapping("/calculate-release-dates/{offenderNo}/reconciliation")
  suspend fun calculateReleaseDatesReconciliation(
    @PathVariable offenderNo: String,
  ): List<MismatchedSentenceCalculation> = reconciliationService.checkPrisonersMatch(offenderNo, suppressTelemetry = true)
}
