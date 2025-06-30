package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.NotFoundException
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import kotlin.onFailure
import kotlin.onSuccess
import kotlin.runCatching
import kotlin.to

@RestController
class IncidentsReconciliationResource(
  private val reconciliationService: IncidentsReconciliationService,
  private val nomisIncidentsApiService: IncidentsNomisApiService,
  private val reportScope: CoroutineScope,
  private val telemetryClient: TelemetryClient,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/incidents/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun incidentsReconciliation() {
    nomisIncidentsApiService.getAgenciesWithIncidents()
      .also { agencyIds ->
        telemetryClient.trackEvent(
          "incidents-reports-reconciliation-requested",
          mapOf("prisonCount" to agencyIds.size),
        )

        reportScope.launch {
          runCatching { reconciliationService.generateReconciliationReport(agencyIds) }
            .onSuccess {
              log.info("Incidents reconciliation report completed with ${it.size} mismatches")
              telemetryClient.trackEvent(
                "incidents-reports-reconciliation-report",
                mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asMap(),
              )
            }
            .onFailure {
              telemetryClient.trackEvent("incidents-reports-reconciliation-report", mapOf("success" to "false"))
              log.error("Incidents reconciliation report failed", it)
            }
        }
      }
  }

  @PreAuthorize("hasRole('NOMIS_INCIDENTS')")
  @GetMapping("/incidents/reconciliation/{nomisIncidentId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Run reconciliation against this incident",
    description = """Retrieves the differences for the incident report. Empty response returned if no differences found.
      Requires NOMIS_INCIDENTS""",
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
        description = "Forbidden to access this endpoint. Requires NOMIS_INCIDENTS",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Incident does not exist",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun generateReconciliationReportForIncident(
    @Schema(description = "Nomis Incident Id", example = "123")
    @PathVariable nomisIncidentId: Long,
  ) = try {
    reconciliationService.checkIncidentMatch(nomisIncidentId)
  } catch (_: NotFound) {
    throw NotFoundException("Incident not found $nomisIncidentId")
  }
}
private fun List<MismatchIncidents>.asMap(): Map<String, String> = this.associate {
  it.agencyId to
    ("open-dps=${it.dpsOpenIncidents}:open-nomis=${it.nomisOpenIncidents}; closed-dps=${it.dpsClosedIncidents}:closed-nomis=${it.nomisClosedIncidents}")
}
