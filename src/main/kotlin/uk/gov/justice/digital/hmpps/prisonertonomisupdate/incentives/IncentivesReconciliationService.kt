package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class IncentivesReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val incentivesApiService: IncentivesApiService,
  @Value("\${reports.incentives.reconciliation.page-size}")
  private val pageSize: Long = 20,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(activePrisonersCount: Long): List<MismatchIncentiveLevel> = activePrisonersCount.asPages(pageSize).flatMap { page ->
    val activePrisoners = getActivePrisonersForPage(page)

    withContext(Dispatchers.Unconfined) {
      activePrisoners.map { async { checkBookingIncentiveMatch(it) } }
    }.awaitAll().filterNotNull()
  }

  private suspend fun getActivePrisonersForPage(page: Pair<Long, Long>) = runCatching { nomisApiService.getActivePrisoners(page.first, page.second).content }
    .onFailure {
      telemetryClient.trackEvent(
        "incentives-reports-reconciliation-mismatch-page-error",
        mapOf(
          "page" to page.first.toString(),
        ),
      )
      log.error("Unable to match entire page of prisoners: $page", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("Page requested: $page, with ${it.size} active prisoners") }

  private suspend fun checkBookingIncentiveMatch(prisonerId: PrisonerIds): MismatchIncentiveLevel? = runCatching {
    // log.debug("Checking booking: ${prisonerId.bookingId}")
    val (nomisIncentiveLevel, dpsIncentiveLevel) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getCurrentIncentive(prisonerId.bookingId) } to
        async { incentivesApiService.getCurrentIncentive(prisonerId.bookingId) }
    }.awaitBoth()

    return if (nomisIncentiveLevel?.iepLevel?.code != dpsIncentiveLevel?.iepCode) {
      MismatchIncentiveLevel(prisonerId, nomisIncentiveLevel?.iepLevel?.code, dpsIncentiveLevel?.iepCode).also { mismatch ->
        log.info("Incentive Mismatch found  $mismatch")
        telemetryClient.trackEvent(
          "incentives-reports-reconciliation-mismatch",
          mapOf(
            "offenderNo" to mismatch.prisonerId.offenderNo,
            "bookingId" to mismatch.prisonerId.bookingId.toString(),
            "nomisIncentiveLevel" to (mismatch.nomisIncentiveLevel ?: "null"),
            "dpsIncentiveLevel" to (mismatch.dpsIncentiveLevel ?: "null"),
          ),
        )
      }
    } else {
      null
    }
  }.onFailure {
    log.error("Unable to match incentives for booking: ${prisonerId.bookingId}", it)
    telemetryClient.trackEvent(
      "incentives-reports-reconciliation-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "bookingId" to prisonerId.bookingId.toString(),
      ),
    )
  }.getOrNull()
}

data class MismatchIncentiveLevel(val prisonerId: PrisonerIds, val nomisIncentiveLevel: String?, val dpsIncentiveLevel: String?)
