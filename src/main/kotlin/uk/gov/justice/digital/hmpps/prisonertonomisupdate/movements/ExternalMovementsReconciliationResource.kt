package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.MismatchedPrisonerCourtMovements
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.MismatchPrisonerTaps
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.MismatchPrisonerTapsSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapActivePrisonersReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapAllPrisonersReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.MismatchedPrisonerTransfer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferScheduleReconciliationService

@PreAuthorize("hasAnyRole('PRISONER_TO_NOMIS__UPDATE__RW', 'ROLE_PRISONER_FROM_NOMIS__REPAIR_MOVEMENTS__RW')")
@RestController
@Tag(name = "External Movements Reconciliation Resource")
class ExternalMovementsReconciliationResource(
  private val allPrisonerReconService: TapAllPrisonersReconciliationService,
  private val activePrisonerReconService: TapActivePrisonersReconciliationService,
  private val courtSchedulerReconService: CourtSchedulerReconciliationService,
  private val transferSchedulerReconService: TransferScheduleReconciliationService,
) {

  @GetMapping("/external-movements/all-taps/{offenderNo}/reconciliation")
  suspend fun allTapsReconciliation(@PathVariable offenderNo: String): List<MismatchPrisonerTapsSummary> = allPrisonerReconService.checkPrisonerTapsMatch(offenderNo, suppressTelemetry = true)

  @GetMapping("/external-movements/active-taps/{offenderNo}/reconciliation")
  suspend fun activeTapsReconciliation(
    @PathVariable offenderNo: String,
  ): List<MismatchPrisonerTaps> = activePrisonerReconService.checkPrisonerTapsMatch(offenderNo, suppressTelemetry = true)

  @GetMapping("/external-movements/court/{offenderNo}/reconciliation")
  suspend fun courtMovementsReconciliation(
    @PathVariable offenderNo: String,
  ): List<MismatchedPrisonerCourtMovements> = courtSchedulerReconService.checkPrisonersMatch(offenderNo, suppressTelemetry = true)

  @GetMapping("/external-movements/transfer/{offenderNo}/reconciliation")
  suspend fun transferMovementsReconciliation(
    @PathVariable offenderNo: String,
  ): List<MismatchedPrisonerTransfer> = transferSchedulerReconService.checkPrisonersMatch(offenderNo, suppressTelemetry = true)
}
