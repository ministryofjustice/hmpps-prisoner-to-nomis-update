package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class CourtSentencingResource(
  private val telemetryClient: TelemetryClient,
  private val courtSentencingReconciliationService: CourtSentencingReconciliationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('ROLE_NOMIS_SENTENCING')")
  @GetMapping("/court-sentencing/court-cases/dps-case-id/{dpsCaseId}/reconciliation")
  suspend fun getCaseReconciliationByDpsCaseId(
    @PathVariable dpsCaseId: String,
  ): MismatchCaseResponse = courtSentencingReconciliationService.manualCheckCaseDps(dpsCaseId = dpsCaseId).also { log.info(it.toString()) }

  @PreAuthorize("hasRole('ROLE_NOMIS_SENTENCING')")
  @GetMapping("/court-sentencing/court-cases/nomis-case-id/{nomisCaseId}/reconciliation")
  suspend fun getCaseReconciliationByNomisCaseId(
    @PathVariable nomisCaseId: Long,
  ): MismatchCaseResponse = courtSentencingReconciliationService.manualCheckCaseNomis(nomisCaseId = nomisCaseId).also { log.info(it.toString()) }
}
