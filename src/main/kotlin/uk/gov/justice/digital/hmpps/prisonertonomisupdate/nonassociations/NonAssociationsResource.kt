package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
class NonAssociationsResource(
  private val telemetryClient: TelemetryClient,
  private val nonAssociationsReconciliationService: NonAssociationsReconciliationService,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/non-associations/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateReconciliationReport() {
    val nonAssociationsCount = nomisApiService.getNonAssociations(0, 1).totalElements

    telemetryClient.trackEvent("non-associations-reports-reconciliation-requested", mapOf("non-associations-nomis-total" to nonAssociationsCount.toString()))
    log.info("Non-associations reconciliation report requested for $nonAssociationsCount non-associations")

    reportScope.launch {
      runCatching { nonAssociationsReconciliationService.generateReconciliationReport(nonAssociationsCount) }
        .onSuccess { listOfLists ->
          val fullResults = listOfLists.flatten()
          log.info("Non-associations reconciliation report completed with ${fullResults.size} mismatches")
          val results = fullResults.take(10) // Only log the first 10 to avoid an insights error with too much data
          val map = mapOf("mismatch-count" to fullResults.size.toString()) +
            results.associate { "${it.id}" to "nomis=${it.nomisNonAssociation}, dps=${it.dpsNonAssociation}" }
          telemetryClient.trackEvent("non-associations-reports-reconciliation-success", map)
          log.info("Non-associations reconciliation report logged")
        }
        .onFailure {
          telemetryClient.trackEvent("non-associations-reports-reconciliation-failed")
          log.error("Non-associations reconciliation report failed", it)
        }
    }
  }

  @PreAuthorize("hasRole('NOMIS_NON_ASSOCIATIONS')")
  @GetMapping("/non-associations/reconciliation/{prisonNumber1}/ns/{prisonNumber2}", produces = [MediaType.APPLICATION_JSON_VALUE])
  @Operation(
    summary = "Run the reconciliation for this prison number",
    description = """Retrieves the differences for a prisoner. Empty response returned if no differences found. 
      Requires ROLE_NOMIS_NON_ASSOCIATIONS""",
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
        description = "Forbidden to access this endpoint. Requires ROLE_NOMIS_NON_ASSOCIATIONS",
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
    @Schema(description = "Prison number aka noms id / offender id display", example = "A1234BC") @PathVariable prisonNumber1: String,
    @Schema(description = "Prison number aka noms id / offender id display", example = "A1234BC") @PathVariable prisonNumber2: String,
  ) = nonAssociationsReconciliationService.checkMatchOrThrowException(NonAssociationIdResponse(prisonNumber1, prisonNumber2)).let {
    it.first.ifEmpty { null }
  }
}
