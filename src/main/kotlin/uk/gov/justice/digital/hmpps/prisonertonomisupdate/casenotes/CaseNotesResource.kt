package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.NotFoundException
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
class CaseNotesResource(
  private val telemetryClient: TelemetryClient,
  private val caseNotesReconciliationService: CaseNotesReconciliationService,
  private val caseNotesService: CaseNotesService,
) {

  @PreAuthorize("hasRole('NOMIS_CASENOTES')")
  @GetMapping("/casenotes/reconciliation/{prisonNumber}", produces = [MediaType.APPLICATION_JSON_VALUE])
  @Operation(
    summary = "Run the reconciliation for this prison number",
    description = """Retrieves the differences for a prisoner. Empty response returned if no differences found. 
      Requires ROLE_NOMIS_CASENOTES""",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Reconciliation differences returned",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint. Requires ROLE_NOMIS_CASENOTES",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Offender does not exist",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun generateReconciliationReportForPrisoner(
    @Schema(description = "Prison number aka noms id / offender id display", example = "A1234BC") @PathVariable prisonNumber: String,
  ) = try {
    caseNotesReconciliationService.checkMatchOrThrowException(prisonNumber)
  } catch (_: NotFound) {
    throw NotFoundException("Offender not found $prisonNumber")
  }

  @PutMapping("/casenotes/{offenderNo}/{dpsId}/resynchronise")
  @PreAuthorize("hasRole('NOMIS_CASENOTES')")
  @Operation(
    summary = "Resynchronises a case note for the given prisoner from DPS to NOMIS",
    description = "Copies a case note from DPS to NOMIS. Used when an unexpected event has happened in DPS that has resulted in the NOMIS data drifting from DPS, so emergency use only. Requires ROLE_NOMIS_CASENOTES",
  )
  suspend fun repairCaseNote(
    @PathVariable offenderNo: String,
    @PathVariable dpsId: String,
  ) {
    caseNotesService.resynchroniseCaseNote(offenderNo, dpsId)
    telemetryClient.trackEvent(
      "to-nomis-synch-casenotes-resynchronisation-repair",
      mapOf(
        "dpsId" to dpsId,
      ),
      null,
    )
  }
}
