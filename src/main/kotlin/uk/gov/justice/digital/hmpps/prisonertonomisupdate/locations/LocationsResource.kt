package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@RestController
class LocationsResource(
  private val telemetryClient: TelemetryClient,
  private val locationsReconciliationService: LocationsReconciliationService,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/locations/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateReconciliationReport() {
    val locationsCount = nomisApiService.getLocations(0, 1).totalElements

    telemetryClient.trackEvent("locations-reports-reconciliation-requested", mapOf("locations-nomis-total" to locationsCount.toString()))
    log.info("Locations reconciliation report requested for $locationsCount locations")

    reportScope.launch {
      runCatching { locationsReconciliationService.generateReconciliationReport(locationsCount) }
        .onSuccess { fullResults ->
          log.info("Locations reconciliation report completed with ${fullResults.size} mismatches")
          val results = fullResults.take(10) // Only log the first 10 to avoid an insights error with too much data
          val map = mapOf("mismatch-count" to fullResults.size.toString()) +
            results.associate { "${it.nomisId},${it.dpsId}" to "nomis=${it.nomisLocation}, dps=${it.dpsLocation}" }
          telemetryClient.trackEvent("locations-reports-reconciliation-success", map)
          log.info("Locations reconciliation report logged")
        }
        .onFailure {
          telemetryClient.trackEvent("locations-reports-reconciliation-failed")
          log.error("Locations reconciliation report failed", it)
        }
    }
  }
}
