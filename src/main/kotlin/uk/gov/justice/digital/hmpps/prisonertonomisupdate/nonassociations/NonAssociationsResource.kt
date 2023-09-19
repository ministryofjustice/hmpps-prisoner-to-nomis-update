package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Hidden
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@RestController
class NonAssociationsResource(
  private val telemetryClient: TelemetryClient,
  private val nonAssociationsReconciliationService: NonAssociationsReconciliationService,
  private val nomisApiService: NomisApiService,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val reportScope: CoroutineScope,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  /**
   * For dev environment only - delete all nonAssociations, for when activities environment is reset
   */
  @Hidden
  @PreAuthorize("hasRole('ROLE_QUEUE_ADMIN')")
  @DeleteMapping("/non-associations")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  suspend fun deleteAllNonAssociations() {
    if (eventFeatureSwitch.isEnabled("DELETEALL")) {
      // TODO nonAssociationsService.deleteAllNonAssociations()
    } else {
      throw RuntimeException("Attempt to delete nonAssociations in wrong environment")
    }
  }

  @PutMapping("/non-associations/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateReconciliationReport() {
    val nonAssociationsCount = nomisApiService.getNonAssociations(0, 1).totalElements

    telemetryClient.trackEvent("non-associations-reports-reconciliation-requested", mapOf("non-associations-total" to nonAssociationsCount.toString()))
    log.info("Non-associations reconciliation report requested for $nonAssociationsCount non-associations")

    reportScope.launch {
      runCatching { nonAssociationsReconciliationService.generateReconciliationReport(nonAssociationsCount) }
        .onSuccess { listOfLists ->
          val results = listOfLists.flatten()
          log.info("Non-associations reconciliation report completed with ${results.size} mismatches")
          val map = mapOf("mismatch-count" to results.size.toString(), "success" to "true") +
            results.associate { "${it.id}" to "nomis=${it.nomisNonAssociation}, dps=${it.dpsNonAssociation}" }
          telemetryClient.trackEvent("non-associations-reports-reconciliation-report", map)
          log.info("Non-associations reconciliation report logged")
        }
        .onFailure {
          telemetryClient.trackEvent("non-associations-reports-reconciliation-report", mapOf("success" to "false"))
          log.error("Non-associations reconciliation report failed", it)
        }
    }
  }
}
