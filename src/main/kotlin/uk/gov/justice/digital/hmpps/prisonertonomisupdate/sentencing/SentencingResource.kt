package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@RestController
class SentencingResource(
  private val telemetryClient: TelemetryClient,
  private val sentencingReconciliationService: SentencingReconciliationService,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
  @Value("\${reports.sentencing.reconciliation.all-prisoners:false}")
  private val allPrisoners: Boolean,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/sentencing/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateSentencingReconciliationReport() {
    val prisonerCount = nomisApiService.getActivePrisoners(
      pageNumber = 0,
      pageSize = 1,
    ).totalElements

    telemetryClient.trackEvent("sentencing-reports-reconciliation-requested", mapOf("prisoner-count" to prisonerCount.toString()))
    log.info("Sentencing Adjustments reconciliation report requested for $prisonerCount prisoners (allPrisoners=$allPrisoners)")

    reportScope.launch {
      runCatching { sentencingReconciliationService.generateReconciliationReport(allPrisoners, prisonerCount) }
        .onSuccess {
          log.info("Sentencing Adjustments reconciliation report completed with ${it.size} mismatches")
          telemetryClient.trackEvent("sentencing-reports-reconciliation-report", mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asMap())
        }
        .onFailure {
          telemetryClient.trackEvent("sentencing-reports-reconciliation-report", mapOf("success" to "false"))
          log.error("Sentencing Adjustments reconciliation report failed", it)
        }
    }
  }
}

private fun List<MismatchSentencingAdjustments>.asMap(): Map<String, String> = this.associate { it.prisonerId.offenderNo to ("${it.nomisCounts.total()}:${it.dpsCounts.total()}") }
