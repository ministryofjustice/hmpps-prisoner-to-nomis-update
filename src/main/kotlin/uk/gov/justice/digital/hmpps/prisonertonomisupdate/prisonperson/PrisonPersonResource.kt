package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@RestController
class PrisonPersonResource(
  private val prisonPersonService: PrisonPersonService,
  private val prisonPersonReconService: PrisonPersonReconService,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
  private val telemetryClient: TelemetryClient,
) {
  @PreAuthorize("hasRole('ROLE_NOMIS_PRISON_PERSON')")
  @PutMapping("/prisonperson/{prisonerNumber}/physical-attributes")
  @Operation(
    summary = "Synchronises an update to DPS physical attributes",
    description = "A manual method for DSP physical attributes to Nomis. Performs in the same way as the automated message based solution - intended for use as a workaround when things go wrong. Requires role <b>NOMIS_PRISON_PERSON</b>",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "The DPS physical attributes synchronised",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to start migration",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun synchronisePhysicalAttributes(
    @Schema(description = "prisonerNumber", required = true) @PathVariable prisonerNumber: String,
  ) {
    telemetryClient.trackEvent("physical-attributes-update-requested", mapOf("offenderNo" to prisonerNumber))
    prisonPersonService.updatePhysicalAttributes(prisonerNumber)
  }

  @PutMapping("/prisonperson/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun reconciliationReport() {
    val activePrisonersCount = nomisApiService.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent("prison-person-reconciliation-report-requested", mapOf("active-prisoners" to activePrisonersCount.toString()))

    reportScope.launch {
      runCatching { prisonPersonReconService.generateReconciliationReport(activePrisonersCount) }
        .onSuccess {
          telemetryClient.trackEvent(
            "prison-person-reconciliation-report-${if (it.isEmpty()) "success" else "failed"}",
            mapOf(
              "active-prisoners" to activePrisonersCount.toString(),
              "mismatch-count" to it.size.toString(),
              "mismatch-prisoners" to it.toString(),
            ),
          )
        }
        .onFailure {
          telemetryClient.trackEvent("prison-person-reconciliation-report-error")
        }
    }
  }
}
