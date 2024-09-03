package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService as PrisonerNomisApiService

@RestController
class ReconciliationResource(
  private val reconciliationService: ReconciliationService,
  private val nomisApiService: PrisonerNomisApiService,
  private val reportScope: CoroutineScope,
  private val telemetryClient: TelemetryClient,
) {
  @PutMapping("/prisonperson/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun reconciliationReport() {
    val activePrisonersCount = nomisApiService.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent("prison-person-reconciliation-report-requested", mapOf("active-prisoners" to activePrisonersCount.toString()))

    reportScope.launch {
      runCatching { reconciliationService.generateReconciliationReport(activePrisonersCount) }
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
