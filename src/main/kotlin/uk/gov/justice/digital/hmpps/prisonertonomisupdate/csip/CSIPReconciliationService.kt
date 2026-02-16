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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.CsipRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Review
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries
import java.time.LocalDate

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

  suspend fun generateCSIPReconciliationReport() {
    val activePrisonersCount = nomisApiService.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent("csip-reports-reconciliation-requested", mapOf("active-prisoners" to activePrisonersCount.toString()))
    log.info("CSIP reconciliation report requested for $activePrisonersCount active prisoners")

    runCatching { generateReconciliationReport(activePrisonersCount) }
      .onSuccess {
        log.info("CSIP reconciliation report completed with ${it.size} mismatches")
        telemetryClient.trackEvent(
          "csip-reports-reconciliation-report",
          mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("csip-reports-reconciliation-report", mapOf("success" to "false"))
        log.error("CSIP reconciliation report failed", it)
      }
  }

  suspend fun generateReconciliationReport(activePrisonersCount: Long): List<MismatchCSIPs> = activePrisonersCount.asPages(pageSize).flatMap { page ->
    val activePrisoners = getActivePrisonersForPage(page)

    withContext(Dispatchers.Unconfined) {
      activePrisoners.map { async { checkCSIPsMatch(it) } }
    }.awaitAll().filterNotNull()
  }

  private suspend fun getActivePrisonersForPage(page: Pair<Long, Long>) = runCatching { nomisApiService.getActivePrisoners(page.first, page.second).content }
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

  suspend fun checkCSIPsMatch(prisonerId: PrisonerIds): MismatchCSIPs? = runCatching {
    checkCSIPsMatchOrThrowException(prisonerId)
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

  suspend fun checkCSIPsMatchOrThrowException(prisonerId: PrisonerIds): MismatchCSIPs? {
    val nomisCSIPList = doApiCallWithRetries { nomisCSIPApiService.getCSIPsForReconciliation(prisonerId.offenderNo) }.offenderCSIPs
    val dpsCSIPList = doApiCallWithRetries { dpsCSIPApiService.getCSIPsForPrisoner(prisonerId.offenderNo) }

    val dpsCSIPs = dpsCSIPList.map { it.toCSIPSummary() }.toSet()
    val nomisCSIPs = nomisCSIPList.map { it.toCSIPSummary() }.toSet()

    val mismatchCount = nomisCSIPList.size - dpsCSIPList.size
    val missingFromNomis = dpsCSIPs - nomisCSIPs
    val missingFromDps = nomisCSIPs - dpsCSIPs
    return if (mismatchCount != 0 || missingFromNomis.isNotEmpty() || missingFromDps.isNotEmpty()) {
      MismatchCSIPs(
        offenderNo = prisonerId.offenderNo,
        dpsCSIPCount = dpsCSIPList.size,
        nomisCSIPCount = nomisCSIPList.size,
        missingFromDps = missingFromDps,
        missingFromNomis = missingFromNomis,
      ).also { mismatch ->
        log.info("CSIP Mismatch found  $mismatch")
        telemetryClient.trackEvent(
          "csip-reports-reconciliation-mismatch",
          mapOf(
            "offenderNo" to mismatch.offenderNo,
            "bookingId" to prisonerId.bookingId.toString(),
            "dpsCount" to mismatch.dpsCSIPCount.toString(),
            "nomisCount" to mismatch.nomisCSIPCount.toString(),
            "missingFromDps" to (mismatch.missingFromDps.joinToString()),
            "missingFromNomis" to (mismatch.missingFromNomis.joinToString()),
          ),
        )
      }
    } else {
      null
    }
  }
}

fun CSIPResponse.toCSIPSummary() = CSIPReportSummary(
  incidentTypeCode = type.code,
  incidentDate = incidentDate.takeIf { it.validDate() },
  incidentTime = incidentTime,
  attendeeCount = reviews.flatMap { it.attendees }.size,
  factorCount = reportDetails.factors.size,
  interviewCount = investigation.interviews?.size ?: 0,
  planCount = plans.size,
  reviewCount = reviews.size,
  scsOutcomeCode = saferCustodyScreening.outcome?.code,
  decisionOutcomeCode = decision.decisionOutcome?.code,
  csipClosedFlag = reviews.any { it.closeCSIP },
)

fun CsipRecord.toCSIPSummary() = CSIPReportSummary(
  incidentTypeCode = referral.incidentType.code,
  incidentDate = referral.incidentDate.takeIf { it.validDate() },
  incidentTime = referral.incidentTime,
  attendeeCount = plan?.reviews?.flatMap { it.attendees }?.size ?: 0,
  factorCount = referral.contributoryFactors.size,
  interviewCount = referral.investigation?.interviews?.size ?: 0,
  planCount = plan?.identifiedNeeds?.size ?: 0,
  reviewCount = plan?.reviews?.size ?: 0,
  scsOutcomeCode = referral.saferCustodyScreeningOutcome?.outcome?.code,
  decisionOutcomeCode = referral.decisionAndActions?.outcome?.code,
  csipClosedFlag = plan?.reviews?.any { it.actions.contains(Review.Actions.CLOSE_CSIP) } ?: false,
)
private fun LocalDate.validDate(): Boolean = this.isAfter(LocalDate.parse("1900-01-01"))

data class CSIPReportSummary(
  val incidentTypeCode: String,
  val incidentDate: LocalDate? = null,
  val incidentTime: String? = null,
  val attendeeCount: Int,
  val factorCount: Int,
  val interviewCount: Int,
  val planCount: Int,
  val reviewCount: Int,
  val scsOutcomeCode: String? = "",
  val decisionOutcomeCode: String? = null,
  val csipClosedFlag: Boolean,
)

data class MismatchCSIPs(
  val offenderNo: String,
  val nomisCSIPCount: Int,
  val dpsCSIPCount: Int,
  val missingFromDps: Set<CSIPReportSummary>,
  val missingFromNomis: Set<CSIPReportSummary>,

)
