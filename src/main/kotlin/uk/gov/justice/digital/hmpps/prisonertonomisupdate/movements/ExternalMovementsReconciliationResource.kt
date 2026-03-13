package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@PreAuthorize("hasAnyRole('PRISONER_TO_NOMIS__UPDATE__RW', 'ROLE_PRISONER_FROM_NOMIS__REPAIR_MOVEMENTS__RW')")
@RestController
@Tag(name = "External Movements Reconciliation Resource")
class ExternalMovementsReconciliationResource(
  private val allPrisonerReconService: TemporaryAbsencesAllPrisonersReconciliationService,
  private val activePrisonerReconService: TemporaryAbsencesActivePrisonersReconciliationService,
) {

  @GetMapping("/external-movements/all-taps/{offenderNo}/reconciliation")
  suspend fun allTapsReconciliation(@PathVariable offenderNo: String): List<MismatchPrisonerTapsSummary> = allPrisonerReconService.checkPrisonerTapsMatch(offenderNo)

  @GetMapping("/external-movements/active-taps/{offenderNo}/reconciliation")
  suspend fun activeTapsReconciliation(
    @PathVariable offenderNo: String,
  ): List<MismatchPrisonerTaps> = activePrisonerReconService.checkPrisonerTapsMatch(offenderNo)
}
