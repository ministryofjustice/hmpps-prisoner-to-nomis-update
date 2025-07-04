package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileDetailsReconciliationService.Companion.TELEMETRY_PREFIX
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import kotlin.onFailure
import kotlin.onSuccess
import kotlin.runCatching
import kotlin.to

@RestController
@PreAuthorize("hasRole('ROLE_NOMIS_UPDATE__RECONCILIATION__R')")
class ContactPersonProfileDetailsReconciliationResource(
  private val reconciliationService: ContactPersonProfileDetailsReconciliationService,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
  private val telemetryClient: TelemetryClient,
) {
  @PutMapping("/contact-person/profile-details/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun reconciliationReport() {
    val activePrisonersCount = nomisApiService.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-report-requested",
      mapOf("active-prisoners" to activePrisonersCount.toString()),
    )

    reportScope.launch {
      runCatching { reconciliationService.generateReconciliationReport(activePrisonersCount) }
        .onSuccess {
          telemetryClient.trackEvent(
            "$TELEMETRY_PREFIX-report-${if (it.isEmpty()) "success" else "failed"}",
            mapOf(
              "active-prisoners" to activePrisonersCount.toString(),
              "mismatch-count" to it.size.toString(),
              "mismatch-prisoners" to it.toString(),
            ),
          )
        }
        .onFailure { e ->
          telemetryClient.trackEvent("$TELEMETRY_PREFIX-report-error", mapOf("error" to "${e.message}"))
          log.error("Failed to generate contact person profile details reconciliation report", e)
        }
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
