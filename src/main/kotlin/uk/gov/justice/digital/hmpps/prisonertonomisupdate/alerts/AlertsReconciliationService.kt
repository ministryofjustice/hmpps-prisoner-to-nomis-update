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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ActivePrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries
import java.time.LocalDate

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

  suspend fun checkActiveAlertsMatch(prisonerId: ActivePrisonerId): MismatchAlerts? = runCatching {
    val nomisActiveAlerts = doApiCallWithRetries { nomisAlertsApiService.getAlertsForReconciliation(prisonerId.offenderNo) }.let { it.latestBookingAlerts + it.previousBookingsAlerts }
    val nomisAlerts = nomisActiveAlerts.map { it.alertCode.code }.toSortedSet()
    val expiredActiveNomisAlerts = nomisActiveAlerts.filter { it.shouldBeInactive() }.map { it.alertCode.code }.toSortedSet()
    val nomisNotExpiredAlerts = nomisAlerts - expiredActiveNomisAlerts
    val dpsAlerts = doApiCallWithRetries { dpsAlertsApiService.getActiveAlertsForPrisoner(prisonerId.offenderNo) }.map { it.alertCode.code }.toSortedSet()

    if (expiredActiveNomisAlerts.isNotEmpty()) {
      telemetryClient.trackEvent(
        "alerts-reports-reconciliation-incorrectly-active",
        mapOf(
          "offenderNo" to prisonerId.offenderNo,
          "bookingId" to prisonerId.bookingId.toString(),
          "incorrectly-active" to (expiredActiveNomisAlerts.joinToString()),
        ),
      )
    }

    val missingFromNomis = dpsAlerts - nomisNotExpiredAlerts
    val missingFromDps = nomisNotExpiredAlerts - dpsAlerts
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

private fun AlertResponse.shouldBeInactive(): Boolean = this.hasNotStarted() || this.hasExpired()
private fun AlertResponse.hasNotStarted(): Boolean = this.date.isAfter(LocalDate.now())
private fun AlertResponse.hasExpired(): Boolean = this.expiryDate != null && this.expiryDate.isBefore(LocalDate.now())

data class MismatchAlerts(
  val offenderNo: String,
  val missingFromDps: Set<String>,
  val missingFromNomis: Set<String>,
)
