package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.fasterxml.jackson.annotation.JsonInclude
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "Court Sentencing Update Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_TO_NOMIS__UPDATE__RW')")
class CourtSentencingResource(
  private val telemetryClient: TelemetryClient,
  private val courtSentencingReconciliationService: CourtSentencingReconciliationService,
  private val courtSentencingRepairService: CourtSentencingRepairService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @GetMapping("/prisoners/{offenderNo}/court-sentencing/court-cases/dps-case-id/{dpsCaseId}/reconciliation")
  suspend fun getCaseReconciliationByDpsCaseId(
    @PathVariable dpsCaseId: String,
    @PathVariable offenderNo: String,
  ): MismatchCaseResponse = courtSentencingReconciliationService.manualCheckCaseDps(offenderNo = offenderNo, dpsCaseId = dpsCaseId)

  @GetMapping("/prisoners/{offenderNo}/court-sentencing/court-cases/nomis-case-id/{nomisCaseId}/reconciliation")
  suspend fun getCaseReconciliationByNomisCaseId(
    @PathVariable nomisCaseId: Long,
    @PathVariable offenderNo: String,
  ): MismatchCaseResponse = courtSentencingReconciliationService.manualCheckCaseNomis(offenderNo = offenderNo, nomisCaseId = nomisCaseId)

  @GetMapping("/prisoners/{offenderNo}/court-sentencing/court-cases/nomis-case-id/{nomisCaseId}/dps-case-id/{dpsCaseId}/reconciliation")
  suspend fun getCaseReconciliationByCaseId(
    @PathVariable offenderNo: String,
    @PathVariable nomisCaseId: Long,
    @PathVariable dpsCaseId: String,
  ): MismatchCaseResponse = courtSentencingReconciliationService.manualCheckCase(offenderNo = offenderNo, nomisCaseId = nomisCaseId, dpsCaseId = dpsCaseId)

  @GetMapping("/prisoners/{offenderNo}/court-sentencing/court-cases/reconciliation")
  suspend fun getCaseReconciliationByOffenderNo(
    @PathVariable offenderNo: String,
  ): List<MismatchCaseResponse> = courtSentencingReconciliationService.manualCheckCaseOffenderNo(offenderNo = offenderNo)

  @PostMapping("/prisoners/court-sentencing/court-cases/reconciliation")
  suspend fun getCaseReconciliationByOffenderNoList(
    @RequestBody offenderNoList: List<String>,
  ): List<List<MismatchCaseResponse>> = courtSentencingReconciliationService.manualCheckCaseOffenderNoList(offenderNoList = offenderNoList)

  @PostMapping("/court-sentencing/court-charges/repair")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Resynchronises a charge.inserted from DPS to NOMIS",
    description = "Used when DPS is in the correct state but NOMIS is wrong, so emergency use only. Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW",
  )
  suspend fun chargeInserted(
    @RequestBody @Valid
    request: CourtChargeRequest,
  ) {
    courtSentencingRepairService.chargeInsertedRepair(request = request)
  }

  @PostMapping("/prisoners/{offenderNo}/court-sentencing/court-case/{dpsCourtCaseId}/booking-repair")
  @Operation(
    summary = "Clones a case to latest booking",
    description = "Used when cases need cloning to latest booking which failed previously, so emergency use only. Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW",
  )
  suspend fun clonedCourtCaseToLatestBooking(
    @PathVariable
    offenderNo: String,
    @PathVariable
    dpsCourtCaseId: String,
  ) {
    courtSentencingRepairService.cloneCaseToLatestBooking(
      offenderNo = offenderNo,
      dpsCourtCaseId = dpsCourtCaseId,
    )
  }

  @PostMapping("/prisoners/{offenderNo}/court-sentencing/court-case/{courtCaseId}/repair")
  @Operation(
    summary = "Resynchronises a court case from DPS to NOMIS",
    description = "Used when a single case needs resynchronising to NOMIS which failed previously, so emergency use only. Only cases without sentences are currently supported. Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Migration payload returned",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = CourtCaseRepairResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to call endpoint",
      ),
      ApiResponse(
        responseCode = "404",
        description = "Either no mapping found for case or case not found for prisoner",
      ),
      ApiResponse(
        responseCode = "409",
        description = "Unable to repair a case which is already sentenced",
      ),
    ],
  )
  suspend fun repairCourtCaseInNomis(
    @PathVariable
    offenderNo: String,
    @Schema(description = "The ID of the court case to be re-synced to NOMIS. For convenience this can either be the DPS court case ID or the NOMIS court case ID.")
    @PathVariable
    courtCaseId: String,
  ): CourtCaseRepairResponse = courtSentencingRepairService.resynchroniseCourtCaseInNomis(
    offenderNo = offenderNo,
    courtCaseId = courtCaseId,
  ).also { telemetryClient.trackEvent("court-sentencing-repair-court-case", mapOf("offenderNo" to offenderNo, "courtCaseId" to courtCaseId), null) }
}

@Schema(description = "Court Charge Request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CourtChargeRequest(
  val offenderNo: String,
  val dpsChargeId: String,
  val dpsCaseId: String,
)

@Schema(description = "Court case repair response")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CourtCaseRepairResponse(
  @Schema(description = "The new NOMIS court case ID")
  val nomisCaseId: Long,
)
