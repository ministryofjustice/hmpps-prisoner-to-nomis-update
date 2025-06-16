package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.fasterxml.jackson.annotation.JsonInclude
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class CourtSentencingResource(
  private val telemetryClient: TelemetryClient,
  private val courtSentencingReconciliationService: CourtSentencingReconciliationService,
  private val courtSentencingRepairService: CourtSentencingRepairService,
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

  @PreAuthorize("hasRole('ROLE_NOMIS_SENTENCING')")
  @GetMapping("/court-sentencing/court-cases/nomis-case-id/{nomisCaseId}/dps-case-id/{dpsCaseId}/reconciliation")
  suspend fun getCaseReconciliationByNomisCaseId(
    @PathVariable nomisCaseId: Long,
    @PathVariable dpsCaseId: String,
  ): MismatchCaseResponse = courtSentencingReconciliationService.manualCheckCase(nomisCaseId = nomisCaseId, dpsCaseId = dpsCaseId).also { log.info(it.toString()) }

  @PreAuthorize("hasRole('ROLE_NOMIS_SENTENCING')")
  @GetMapping("/prisoners/{offenderNo}/court-sentencing/court-cases/reconciliation")
  suspend fun getCaseReconciliationByOffenderNo(
    @PathVariable offenderNo: String,
  ): List<MismatchCaseResponse> = courtSentencingReconciliationService.manualCheckCaseOffenderNo(offenderNo = offenderNo).also { log.info(it.toString()) }

  @PreAuthorize("hasRole('ROLE_NOMIS_SENTENCING')")
  @PostMapping("/court-sentencing/court-charges/repair")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Resynchronises a charge.inserted from DPS to NOMIS",
    description = "Used when DPS is in the correct state but NOMIS is wrong, so emergency use only. Requires ROLE_MIGRATE_SENTENCING",
  )
  suspend fun chargeInserted(
    @RequestBody @Valid
    request: CourtChargeRequest,
  ) {
    courtSentencingRepairService.chargeInsertedRepair(request = request)
  }
}

@Schema(description = "Court Charge Request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CourtChargeRequest(
  val offenderNo: String,
  val dpsChargeId: String,
  val dpsCaseId: String,
)
