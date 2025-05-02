package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitBalanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto
import kotlin.Long

@Service
class VisitBalanceReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsVisitBalanceApiService: VisitBalanceDpsApiService,
  private val nomisVisitBalanceApiService: VisitBalanceNomisApiService,
  private val nomisApiService: NomisApiService,
  @Value("\${reports.visit-balance.reconciliation.page-size}")
  private val pageSize: Long = 20,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(activePrisonersCount: Long): List<MismatchVisitBalance> = activePrisonersCount.asPages(pageSize).flatMap { page ->
    val activePrisoners = getActivePrisonersForPage(page)

    withContext(Dispatchers.Unconfined) {
      activePrisoners.map { async { checkVisitBalanceMatch(it) } }
    }.awaitAll().filterNotNull()
  }

  private suspend fun getActivePrisonersForPage(page: Pair<Long, Long>) = runCatching { nomisApiService.getActivePrisoners(page.first, page.second).content }
    .onFailure {
      telemetryClient.trackEvent(
        "visitbalance-reports-reconciliation-mismatch-page-error",
        mapOf(
          "page" to page.first.toString(),
        ),
      )
      log.error("Unable to match entire page of prisoners: $page", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("Page requested: $page, with ${it.size} active prisoners") }

  suspend fun checkVisitBalanceMatch(prisonerId: PrisonerIds): MismatchVisitBalance? = runCatching {
    // Note that the lack of a balance is treated identically to a balance of 0, since it could be created and then not
    // used in either system
    val nomisVisitBalance = doApiCallWithRetries { nomisVisitBalanceApiService.getVisitBalance(prisonerId.offenderNo) }?.toBalance() ?: PrisonerBalance()
    val dpsVisitBalance = doApiCallWithRetries { dpsVisitBalanceApiService.getVisitBalance(prisonerId.offenderNo) }?.toBalance() ?: PrisonerBalance()

    return if (nomisVisitBalance != dpsVisitBalance) {
      MismatchVisitBalance(prisonNumber = prisonerId.offenderNo, dpsVisitBalance = dpsVisitBalance, nomisVisitBalance = nomisVisitBalance).also { mismatch ->
        log.info("VisitBalance Mismatch found  $mismatch")
        telemetryClient.trackEvent(
          "visitbalance-reports-reconciliation-mismatch",
          mapOf(
            "prisonNumber" to mismatch.prisonNumber,
            "bookingId" to prisonerId.bookingId.toString(),
            "nomisVisitBalance" to mismatch.nomisVisitBalance.visitBalance.toString(),
            "dpsVisitBalance" to mismatch.dpsVisitBalance.visitBalance.toString(),
            "nomisPrivilegedVisitBalance" to mismatch.nomisVisitBalance.privilegedVisitBalance.toString(),
            "dpsPrivilegedVisitBalance" to mismatch.dpsVisitBalance.privilegedVisitBalance.toString(),
          ),
        )
      }
    } else {
      null
    }
  }.onFailure {
    log.error("Unable to match visit balance for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId}", it)
    telemetryClient.trackEvent(
      "visitbalance-reports-reconciliation-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "bookingId" to prisonerId.bookingId.toString(),
      ),
    )
  }.getOrNull()

  suspend fun checkVisitBalanceMatch(offenderNo: String): MismatchVisitBalance? = checkVisitBalanceMatch(PrisonerIds(0, offenderNo))
}

fun VisitBalanceResponse.toBalance() = PrisonerBalance(visitBalance = remainingVisitOrders, privilegedVisitBalance = remainingPrivilegedVisitOrders)
fun PrisonerBalanceDto.toBalance() = PrisonerBalance(visitBalance = voBalance, privilegedVisitBalance = pvoBalance)

data class MismatchVisitBalance(
  val prisonNumber: String,
  val dpsVisitBalance: PrisonerBalance,
  val nomisVisitBalance: PrisonerBalance,
)

data class PrisonerBalance(
  val visitBalance: Int = 0,
  val privilegedVisitBalance: Int = 0,
)
