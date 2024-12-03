package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

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
}
