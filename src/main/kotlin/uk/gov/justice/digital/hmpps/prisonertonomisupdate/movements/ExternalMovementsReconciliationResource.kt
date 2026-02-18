package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "External Movements Reconciliation Resource")
class ExternalMovementsReconciliationResource(
  private val reconciliationService: TemporaryAbsencesAllPrisonersReconciliationService,
) {

  @PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
  @GetMapping("/external-movements/all-taps/{offenderNo}/reconciliation")
  suspend fun allTapsReconciliation(@PathVariable offenderNo: String): List<MismatchPrisonerTaps> = reconciliationService.checkPrisonerTapsMatch(offenderNo)
}
