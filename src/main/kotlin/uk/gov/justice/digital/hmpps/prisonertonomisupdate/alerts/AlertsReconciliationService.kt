package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries

@Service
class AlertsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsAlertsApiService: AlertsDpsApiService,
  private val nomisAlertsApiService: AlertsNomisApiService,
  private val nomisApiService: NomisApiService,
  @Value("\${reports.alerts.reconciliation.page-size}")
  private val pageSize: Long = 20,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(activePrisonersCount: Long): List<MismatchAlerts> {
    return activePrisonersCount.asPages(pageSize).flatMap { page ->
      val activePrisoners = getActivePrisonersForPage(page)

      withContext(Dispatchers.Unconfined) {
        activePrisoners.map { async { checkActiveAlertsMatch(it) } }
      }.awaitAll().filterNotNull()
    }
  }

  private suspend fun getActivePrisonersForPage(page: Pair<Long, Long>) =
    runCatching { nomisApiService.getActivePrisoners(page.first, page.second).content }
      .onFailure {
        telemetryClient.trackEvent(
          "alerts-reports-reconciliation-mismatch-page-error",
          mapOf(
            "page" to page.first.toString(),
          ),
        )
        log.error("Unable to match entire page of prisoners: $page", it)
      }
      .getOrElse { emptyList() }
      .also { log.info("Page requested: $page, with ${it.size} active prisoners") }

  suspend fun checkActiveAlertsMatch(prisonerId: PrisonerIds): MismatchAlerts? = runCatching {
    val nomisAlerts = doApiCallWithRetries { nomisAlertsApiService.getAlertsForReconciliation(prisonerId.offenderNo) }.let { it.latestBookingAlerts + it.previousBookingsAlerts }
      .map { it.alertCode.code }.toSortedSet()
    val dpsAlerts = doApiCallWithRetries { dpsAlertsApiService.getActiveAlertsForPrisoner(prisonerId.offenderNo) }
      .map { it.alertCode.code }.toSortedSet()

    val missingFromNomis = dpsAlerts - nomisAlerts
    val missingFromDps = nomisAlerts - dpsAlerts
    return if (missingFromNomis.isNotEmpty() || missingFromDps.isNotEmpty()) {
      MismatchAlerts(offenderNo = prisonerId.offenderNo, missingFromDps = missingFromDps, missingFromNomis = missingFromNomis).also { mismatch ->
        log.info("Alerts Mismatch found  $mismatch")
        telemetryClient.trackEvent(
          "alerts-reports-reconciliation-mismatch",
          mapOf(
            "offenderNo" to mismatch.offenderNo,
            "bookingId" to prisonerId.bookingId.toString(),
            "missingFromDps" to (mismatch.missingFromDps.joinToString()),
            "missingFromNomis" to (mismatch.missingFromNomis.joinToString()),
          ),
        )
      }
    } else {
      null
    }
  }.onFailure {
    log.error("Unable to match alerts for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId}", it)
    telemetryClient.trackEvent(
      "alerts-reports-reconciliation-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "bookingId" to prisonerId.bookingId.toString(),
      ),
    )
  }.getOrNull()
}

data class MismatchAlerts(
  val offenderNo: String,
  val missingFromDps: Set<String>,
  val missingFromNomis: Set<String>,
)
