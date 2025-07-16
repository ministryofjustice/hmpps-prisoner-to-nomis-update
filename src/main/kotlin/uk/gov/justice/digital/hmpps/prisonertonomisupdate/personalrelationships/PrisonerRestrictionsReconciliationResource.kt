package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.PrisonerReconciliationReconciliationService.Companion.TELEMETRY_PRISONER_PREFIX

@RestController
class PrisonerRestrictionsReconciliationResource(
  private val telemetryClient: TelemetryClient,
  private val reconciliationService: PrisonerReconciliationReconciliationService,
  private val reportScope: CoroutineScope,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('ROLE_NOMIS_UPDATE__RECONCILIATION__R')")
  @PostMapping("/contact-person/prisoner-restriction/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generatePrisonerRestrictionsReconciliationReport() {
    telemetryClient.trackEvent(
      "$TELEMETRY_PRISONER_PREFIX-requested",
      mapOf(),
    )

    reportScope.launch {
      runCatching { reconciliationService.generatePrisonerRestrictionsReconciliationReport() }
        .onSuccess {
          log.info("Prisoner restrictions reconciliation report completed with ${it.mismatches.size} mismatches")
          telemetryClient.trackEvent(
            "$TELEMETRY_PRISONER_PREFIX-report",
            mapOf(
              "restrictions-count" to it.itemsChecked.toString(),
              "pages-count" to it.pagesChecked.toString(),
              "mismatch-count" to it.mismatches.size.toString(),
              "success" to "true",
            ) + it.mismatches.asMap(),
          )
        }
        .onFailure {
          telemetryClient.trackEvent("$TELEMETRY_PRISONER_PREFIX-report", mapOf("success" to "false"))
          log.error("Prisoner contacts reconciliation report failed", it)
        }
    }
  }
}
private fun List<MismatchPrisonerRestriction>.asMap(): Map<String, String> = this.take(10).associate { it.restrictionId.toString() to "TODO" }
