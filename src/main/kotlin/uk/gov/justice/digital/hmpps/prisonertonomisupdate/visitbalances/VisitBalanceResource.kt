package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

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
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
class VisitBalanceResource(
  private val telemetryClient: TelemetryClient,
  private val reconciliationService: VisitBalanceReconciliationService,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/visit-balance/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateReconciliationReport() {
    val activePrisonersCount = nomisApiService.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent(
      "visitbalance-reports-reconciliation-requested",
      mapOf("active-prisoners" to activePrisonersCount.toString()),
    )
    log.info("Visit Balance reconciliation report requested for $activePrisonersCount active prisoners")

    reportScope.launch {
      runCatching { reconciliationService.generateReconciliationReport(activePrisonersCount) }
        .onSuccess {
          log.info("Visit balance reconciliation report completed with ${it.size} mismatches")
          telemetryClient.trackEvent(
            "visitbalance-reports-reconciliation-report",
            mapOf("mismatch-count" to it.size.toString(), "success" to "true"),
          )
        }
        .onFailure {
          telemetryClient.trackEvent("visitbalance-reports-reconciliation-report", mapOf("success" to "false"))
          log.error("Visit balance reconciliation report failed", it)
        }
    }
  }

  @PreAuthorize("hasRole('NOMIS_VISIT_BALANCE')")
  @GetMapping("/visit-balance/reconciliation/{prisonNumber}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Run the reconciliation for this prison number",
    description = """Retrieves the differences for a prisoner. Empty response returned if no differences found. 
      Requires NOMIS_VISIT_BALANCE""",
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
        description = "Forbidden to access this endpoint. Requires NOMIS_VISIT_BALANCE",
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
  ) = reconciliationService.checkVisitBalanceMatch(prisonNumber)
}
