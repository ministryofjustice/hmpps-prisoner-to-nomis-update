package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
class CaseNotesResource(
  private val telemetryClient: TelemetryClient,
  private val caseNotesReconciliationService: CaseNotesReconciliationService,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/casenotes/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateReconciliationReport(
    @Schema(description = "Whether to reconcile all or only active case notes", required = false, defaultValue = "true")
    @RequestParam(defaultValue = "true")
    activeOnly: Boolean,
  ) {
    val prisonersCount = if (activeOnly) {
      nomisApiService.getActivePrisoners(0, 1).totalElements
    } else {
      nomisApiService.getAllPrisonersPaged(0, 1).totalElements
    }

    telemetryClient.trackEvent(
      "casenotes-reports-reconciliation-requested",
      mapOf("casenotes-nomis-total" to prisonersCount.toString(), "activeOnly" to activeOnly.toString()),
    )
    log.info("casenotes reconciliation report requested for $prisonersCount prisoners")

    reportScope.launch {
      runCatching { caseNotesReconciliationService.generateReconciliationReport(prisonersCount, activeOnly) }
        .onSuccess {
          log.info("Casenotes reconciliation report completed with ${it.size} mismatches")
          telemetryClient.trackEvent(
            "casenotes-reports-reconciliation-report",
            mapOf("mismatch-count" to it.size.toString(), "success" to "true"),
          )
        }
        .onFailure {
          telemetryClient.trackEvent("casenotes-reports-reconciliation-report", mapOf("success" to "false", "error" to (it.message ?: "")))
          log.error("Casenotes reconciliation report failed", it)
        }
    }
  }

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
  } catch (notFound: NotFound) {
    throw NotFoundException("Offender not found $prisonNumber")
  }
}

class NotFoundException(message: String) : RuntimeException(message)
