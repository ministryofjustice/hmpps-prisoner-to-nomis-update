package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Staff Reconciliation Resource")
class StaffReconciliationResource(
  private val reconciliationService: StaffReconciliationService,
) {
  @PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
  @GetMapping("/staff/nomis-staff-id/{nomisStaffId}/reconciliation")
  suspend fun getStaffReconciliationByNomisId(@PathVariable nomisStaffId: Long): MismatchStaff? = reconciliationService.checkStaffMatch(nomisStaffId)
}
