package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent

@RestController
class OrganisationsResource(
  private val telemetryClient: TelemetryClient,
  private val reconciliationService: OrganisationsReconciliationService,
  private val reportScope: CoroutineScope,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/organisations/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateOrganisationsReconciliationReport() {
    val counts = reconciliationService.getOrganisationsCounts()

    telemetryClient.trackEvent("organisations-reports-reconciliation-requested", mapOf("organisations" to counts.nomisCount.toString()))

    if (counts.nomisCount != counts.dpsCount) {
      telemetryClient.trackEvent(
        "organisations-reports-reconciliation-mismatch",
        mapOf(
          "dpsCount" to "$counts.dpsCount",
          "nomisCount" to "$counts.nomisCount",
        ),
      )
    }

    reportScope.launch {
      runCatching { reconciliationService.generateReconciliationReport(counts.nomisCount) }
        .onSuccess {
          log.info("Organisations reconciliation report completed with ${it.size} mismatches")
          telemetryClient.trackEvent("organisations-reports-reconciliation-report", mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asTelemetry())
        }
        .onFailure {
          telemetryClient.trackEvent("organisations-reports-reconciliation-report", mapOf("success" to "false"))
          log.error("Organisations reconciliation report failed", it)
        }
    }
  }
}

private fun List<MismatchOrganisation>.asTelemetry(): Pair<String, String> = "organisationIds" to (this.map { it.organisationId }.toString())
