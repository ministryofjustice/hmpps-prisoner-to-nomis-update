package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

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
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.NotFoundException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
class CSIPResource(
  private val telemetryClient: TelemetryClient,
  private val reconciliationService: CSIPReconciliationService,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/csip/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateCSIPReconciliationReport() {
    val activePrisonersCount = nomisApiService.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent("csip-reports-reconciliation-requested", mapOf("active-prisoners" to activePrisonersCount.toString()))
    log.info("CSIP reconciliation report requested for $activePrisonersCount active prisoners")

    reportScope.launch {
      runCatching { reconciliationService.generateReconciliationReport(activePrisonersCount) }
        .onSuccess {
          log.info("CSIP reconciliation report completed with ${it.size} mismatches")
          telemetryClient.trackEvent(
            "csip-reports-reconciliation-report",
            mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asMap(),
          )
        }
        .onFailure {
          telemetryClient.trackEvent("csip-reports-reconciliation-report", mapOf("success" to "false"))
          log.error("CSIP reconciliation report failed", it)
        }
    }
  }

  @PreAuthorize("hasRole('NOMIS_CSIP')")
  @GetMapping("/csip/reconciliation/{prisonNumber}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Run csip reconciliation against this prisoner",
    description = """Retrieves the differences for csip reports against a specific prisoner. Empty response returned if no differences found.
      Requires NOMIS_CSIP""",
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
        description = "Forbidden to access this endpoint. Requires NOMIS_CSIP",
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
