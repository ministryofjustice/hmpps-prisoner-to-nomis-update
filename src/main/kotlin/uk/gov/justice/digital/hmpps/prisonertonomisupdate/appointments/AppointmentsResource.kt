package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

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
class AppointmentsResource(
  private val telemetryClient: TelemetryClient,
  private val appointmentsReconciliationService: AppointmentsReconciliationService,
  private val reportScope: CoroutineScope,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/appointments/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateReconciliationReport() {
    telemetryClient.trackEvent("appointments-reports-reconciliation-requested")
    log.info("Appointments reconciliation report requested")

    reportScope.launch {
      runCatching { appointmentsReconciliationService.generateReconciliationReport() }
        .onSuccess { fullResults ->
          log.info("Appointments reconciliation report completed with ${fullResults.size} mismatches")
          val results = fullResults.take(10) // Only log the first 10 to avoid an insights error with too much data
          val map = mapOf("mismatch-count" to fullResults.size.toString()) +
            results.associate { "${it.nomisId},${it.dpsId}" to "nomis=${it.nomisAppointment}, dps=${it.dpsAppointment}" }
          telemetryClient.trackEvent("appointments-reports-reconciliation-success", map)
          log.info("Appointments reconciliation report logged")
        }
        .onFailure {
          telemetryClient.trackEvent("appointments-reports-reconciliation-failed")
          log.error("Appointments reconciliation report failed", it)
        }
    }
  }
}
