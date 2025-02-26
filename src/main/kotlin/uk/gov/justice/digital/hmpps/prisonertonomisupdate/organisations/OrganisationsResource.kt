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
  private val reconciliationService: OrganisationReconciliationService,
  private val nomisApiService: OrganisationsNomisApiService,
  private val reportScope: CoroutineScope,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/organisations/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateOrganisationsReconciliationReport() {
    val organisationsCount = nomisApiService.getCorporateOrganisationIds(0, 1).totalElements

    telemetryClient.trackEvent("organisations-reports-reconciliation-requested", mapOf("organisations" to organisationsCount.toString()))

    reportScope.launch {
      runCatching { reconciliationService.generateReconciliationReport(organisationsCount) }
        .onSuccess {
          log.info("Organisations reconciliation report completed with ${it.size} mismatches")
          telemetryClient.trackEvent("organisations-reports-reconciliation-report", mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asMap())
        }
        .onFailure {
          telemetryClient.trackEvent("organisations-reports-reconciliation-report", mapOf("success" to "false"))
          log.error("Organisations reconciliation report failed", it)
        }
    }
  }
}

private fun List<MismatchOrganisation>.asMap(): Map<String, String> = this.associate { "$it.organisationId" to "TODO" }
