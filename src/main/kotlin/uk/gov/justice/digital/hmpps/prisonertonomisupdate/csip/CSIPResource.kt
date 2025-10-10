package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.NotFoundException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@Tag(name = "CSIP Update Resource")
class CSIPResource(
  private val reconciliationService: CSIPReconciliationService,
) {

  @PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
  @GetMapping("/csip/reconciliation/{prisonNumber}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Run csip reconciliation against this prisoner",
    description = """Retrieves the differences for csip reports against a specific prisoner. Empty response returned if no differences found.
      Requires PRISONER_TO_NOMIS__UPDATE__RW""",
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
        description = "Forbidden to access this endpoint. Requires PRISONER_TO_NOMIS__UPDATE__RW",
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
    @Schema(description = "Prison number aka noms id / offender id display", example = "A1234BC")
    @PathVariable prisonNumber: String,
  ) = try {
    reconciliationService.checkCSIPsMatchOrThrowException(PrisonerIds(0, prisonNumber))
  } catch (_: NotFound) {
    throw NotFoundException("Offender not found $prisonNumber")
  }
}

fun List<MismatchCSIPs>.asMap(): Map<String, String> = this.associate {
  it.offenderNo to
    (
      "total-dps=${it.dpsCSIPCount}:total-nomis=${it.nomisCSIPCount}; " +
        "missing-dps=${it.missingFromDps.size}:missing-nomis=${it.missingFromNomis.size}"
      )
}
