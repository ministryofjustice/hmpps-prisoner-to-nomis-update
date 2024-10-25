package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

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
class CSIPReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsCSIPApiService: CSIPDpsApiService,
  private val nomisApiService: NomisApiService,
  private val nomisCSIPApiService: CSIPNomisApiService,
  @Value("\${reports.csip.reconciliation.page-size}")
  private val pageSize: Long = 20,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(activePrisonersCount: Long): List<MismatchCSIPs> = activePrisonersCount.asPages(pageSize).flatMap { page ->
    val activePrisoners = getActivePrisonersForPage(page)

    withContext(Dispatchers.Unconfined) {
      activePrisoners.map { async { checkOpenCSIPsMatch(it) } }
    }.awaitAll().filterNotNull()
  }

  private suspend fun getActivePrisonersForPage(page: Pair<Long, Long>) =
    runCatching { nomisApiService.getActivePrisoners(page.first, page.second).content }
      .onFailure {
        telemetryClient.trackEvent(
          "csip-reports-reconciliation-mismatch-page-error",
          mapOf(
            "page" to page.first.toString(),
          ),
        )
        log.error("Unable to match entire page of prisoners: $page", it)
      }
      .getOrElse { emptyList() }
      .also { log.info("Page requested: $page, with ${it.size} active prisoners") }

  suspend fun checkOpenCSIPsMatch(prisonerId: PrisonerIds): MismatchCSIPs? = runCatching {
    val nomisCSIPs = doApiCallWithRetries { nomisCSIPApiService.getCSIPsForReconciliation(prisonerId.offenderNo) }.offenderCSIPs.size
    val dpsCSIPs = doApiCallWithRetries { dpsCSIPApiService.getCSIPsForPrisoner(prisonerId.offenderNo) }.size

    val mismatchCSIP = dpsCSIPs - nomisCSIPs
    return if (mismatchCSIP != 0) {
      MismatchCSIPs(offenderNo = prisonerId.offenderNo, nomisCSIPCount = nomisCSIPs, dpsCSIPCount = dpsCSIPs).also { mismatch ->
        log.info("CSIP Mismatch found  $mismatch")
        telemetryClient.trackEvent(
          "csip-reports-reconciliation-mismatch",
          mapOf(
            "offenderNo" to mismatch.offenderNo,
            "bookingId" to prisonerId.bookingId.toString(),
            "dpsCSIPCount" to mismatch.dpsCSIPCount.toString(),
            "nomisCSIPCount" to mismatch.nomisCSIPCount.toString(),
          ),
        )
      }
    } else {
      null
    }
  }.onFailure {
    log.error("Unable to match csips for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId}", it)
    telemetryClient.trackEvent(
      "csip-reports-reconciliation-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "bookingId" to prisonerId.bookingId.toString(),
      ),
    )
  }.getOrNull()
}

data class MismatchCSIPs(
  val offenderNo: String,
  val nomisCSIPCount: Int,
  val dpsCSIPCount: Int,
)
