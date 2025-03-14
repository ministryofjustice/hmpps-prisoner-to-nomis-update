package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonReconciliationService.Companion.TELEMETRY_PREFIX

@RestController
class ContactPersonReconciliationResource(
  private val telemetryClient: TelemetryClient,
  private val reconciliationService: ContactPersonReconciliationService,
  private val reportScope: CoroutineScope,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/contact-person/prisoner-contact/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generatePrisonerContactReconciliationReport() {
    telemetryClient.trackEvent("$TELEMETRY_PREFIX-requested", mapOf())

    reportScope.launch {
      runCatching { reconciliationService.generatePrisonerContactReconciliationReport() }
        .onSuccess {
          log.info("Prisoner contacts reconciliation report completed with ${it.size} mismatches")
          telemetryClient.trackEvent(
            "$TELEMETRY_PREFIX-report",
            mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asMap(),
          )
        }
        .onFailure {
          telemetryClient.trackEvent("$TELEMETRY_PREFIX-report", mapOf("success" to "false"))
          log.error("Prisoner contacts reconciliation report failed", it)
        }
    }
  }
}

private fun List<MismatchPrisonerContacts>.asMap(): Map<String, String> = this.associate { it.offenderNo to "TODO" }
