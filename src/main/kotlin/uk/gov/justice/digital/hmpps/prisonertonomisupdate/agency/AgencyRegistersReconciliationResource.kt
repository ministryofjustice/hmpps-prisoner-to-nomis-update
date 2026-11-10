package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Agency Registers Reconciliation Resource")
class AgencyRegistersReconciliationResource(
  private val reconciliationService: AgencyRegistersReconciliationService,
) {
  @PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
  @GetMapping("/agencies/{agencyId}/reconciliation")
  @Operation(description = "Returns a mismatch with the full NOMIS and DPS agency payloads, where available, or an empty response when the agencies match.")
  suspend fun getAgencyReconciliation(@PathVariable agencyId: String): MismatchAgency? = reconciliationService.checkNonPrisonMatch(agencyId)

  @PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
  @GetMapping("/prisons/{agencyId}/reconciliation")
  @Operation(description = "Returns a mismatch with the partial NOMIS and DPS prison payloads, where available, or an empty response when the prisons match.")
  suspend fun getPrisonReconciliation(@PathVariable agencyId: String): MismatchAgency? = reconciliationService.checkPrisonMatch(agencyId)
}
