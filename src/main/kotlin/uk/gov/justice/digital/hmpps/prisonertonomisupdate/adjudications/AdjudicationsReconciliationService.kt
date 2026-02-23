package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AdjudicationADAAwardSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries
import java.time.LocalDate

@Service
class AdjudicationsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val adjudicationsApiService: AdjudicationsApiService,
  private val adjudicationsMappingService: AdjudicationsMappingService,
  @Value("\${reports.adjudications.reconciliation.page-size}")
  private val pageSize: Long = 20,
  @Value("\${reports.adjudications.reconciliation.migration-date}")
  private val nomisMigrationDate: LocalDate,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateAdjudicationsReconciliationReport() {
    val activePrisonersCount = nomisApiService.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent("adjudication-reports-reconciliation-requested", mapOf("active-prisoners" to activePrisonersCount.toString()))
    log.info("Adjudication reconciliation report requested for $activePrisonersCount active prisoners")

    runCatching { generateReconciliationReport(activePrisonersCount) }
      .onSuccess {
        log.info("Adjudication reconciliation report completed with ${it.size} mismatches")
        telemetryClient.trackEvent("adjudication-reports-reconciliation-report", mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asMap())
      }
      .onFailure {
        telemetryClient.trackEvent("adjudication-reports-reconciliation-report", mapOf("success" to "false"))
        log.error("Adjudication reconciliation report failed", it)
      }
  }

  private fun List<MismatchAdjudicationAdaPunishments>.asMap(): Map<String, String> = this.associate { it.prisonerId.offenderNo to ("${it.nomisAda.count}:${it.dpsAdas.count},${it.nomisAda.days}:${it.dpsAdas.days}") }

  suspend fun generateReconciliationReport(activePrisonersCount: Long): List<MismatchAdjudicationAdaPunishments> = activePrisonersCount.asPages(pageSize).flatMap { page ->
    val activePrisoners = getActivePrisonersForPage(page)

    withContext(Dispatchers.Unconfined) {
      activePrisoners.map { async { checkADAPunishmentsMatch(it) } }
    }.awaitAll().filterNotNull()
  }

  private suspend fun getActivePrisonersForPage(page: Pair<Long, Long>) = runCatching { nomisApiService.getActivePrisoners(page.first, page.second).content }
    .onFailure {
      telemetryClient.trackEvent(
        "adjudication-reports-reconciliation-mismatch-page-error",
        mapOf(
          "page" to page.first.toString(),
        ),
      )
      log.error("Unable to match entire page of prisoners: $page", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("Page requested: $page, with ${it.size} active prisoners") }

  suspend fun checkADAPunishmentsMatch(prisonerId: PrisonerIds): MismatchAdjudicationAdaPunishments? = runCatching {
    val nomisSummary = doApiCallWithRetries { nomisApiService.getAdaAwardsSummary(prisonerId.bookingId) }
    val dpsAdjudications = doApiCallWithRetries { adjudicationsApiService.getAdjudicationsByBookingId(prisonerId.bookingId) }

    val nomisAdaSummary: AdaSummary = nomisSummary.toAdaSummary()
    val dpsAdaSummary: AdaSummary = dpsAdjudications.toAdaSummary()
    return if (nomisAdaSummary != dpsAdaSummary && mismatchNotDueToAMerge(prisonerId, nomisSummary, dpsAdjudications)) {
      MismatchAdjudicationAdaPunishments(prisonerId = prisonerId, dpsAdas = dpsAdaSummary, nomisAda = nomisAdaSummary).also { mismatch ->
        log.info("Adjudications Mismatch found  $mismatch")
        telemetryClient.trackEvent(
          "adjudication-reports-reconciliation-mismatch",
          mapOf(
            "offenderNo" to mismatch.prisonerId.offenderNo,
            "bookingId" to mismatch.prisonerId.bookingId.toString(),
            "nomisAdaCount" to (mismatch.nomisAda.count.toString()),
            "dpsAdaCount" to (mismatch.dpsAdas.count.toString()),
            "nomisAdaDays" to (mismatch.nomisAda.days.toString()),
            "dpsAdaDays" to (mismatch.dpsAdas.days.toString()),
          ),
        )
      }
    } else {
      null
    }
  }.onFailure {
    log.error("Unable to match adjudication for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId}", it)
    telemetryClient.trackEvent(
      "adjudication-reports-reconciliation-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "bookingId" to prisonerId.bookingId.toString(),
      ),
    )
  }.getOrNull()

  private suspend fun mismatchNotDueToAMerge(prisonerId: PrisonerIds, nomisSummary: AdjudicationADAAwardSummaryResponse, dpsAdjudications: List<ReportedAdjudicationDto>): Boolean {
    val merges = nomisApiService.mergesSinceDate(prisonerId.offenderNo, nomisMigrationDate)
    if (merges.isNotEmpty()) {
      val adjudicationsNotPresentOnDPSBooking = adjudicationsNotPresentOnDPSBooking(nomisSummary, dpsAdjudications)
      if (adjudicationsNotPresentOnDPSBooking.isNotEmpty()) {
        val dpsAdjudicationsOnPreviousBooking = merges.flatMap { doApiCallWithRetries { adjudicationsApiService.getAdjudicationsByBookingId(it.previousBookingId) } }
        val allAdjudications = dpsAdjudicationsOnPreviousBooking + dpsAdjudications
        val nomisAdaSummary = nomisSummary.toAdaSummary()
        val dpsAdaSummary = allAdjudications.toAdaSummary()
        val telemetry = mapOf(
          "offenderNo" to prisonerId.offenderNo,
          "bookingId" to prisonerId.bookingId.toString(),
          "nomisAdaCount" to (nomisAdaSummary.count.toString()),
          "originalDpsAdaCount" to (dpsAdjudications.toAdaSummary().count.toString()),
          "dpsAdaCount" to (dpsAdaSummary.count.toString()),
          "nomisAdaDays" to (nomisAdaSummary.days.toString()),
          "originalDpsAdaDays" to (dpsAdjudications.toAdaSummary().days.toString()),
          "dpsAdaDays" to (dpsAdaSummary.days.toString()),
          "mergeDate" to (merges.last().requestDateTime.toString()),
          "mergeFrom" to (merges.last().deletedOffenderNo),
          "missingAdjudications" to (adjudicationsNotPresentOnDPSBooking.joinToString()),
        )

        if (nomisAdaSummary == dpsAdaSummary) {
          telemetryClient.trackEvent("adjudication-reports-reconciliation-merge-mismatch-resolved", telemetry)
          return false
        } else {
          telemetryClient.trackEvent("adjudication-reports-reconciliation-merge-mismatch", telemetry)
        }
      }
    }
    return true
  }

  private suspend fun adjudicationsNotPresentOnDPSBooking(nomisSummary: AdjudicationADAAwardSummaryResponse, dpsAdjudications: List<ReportedAdjudicationDto>): List<Long> {
    val dpsAdjudicationNumbers = dpsAdjudications.mapNotNull { adjudicationsMappingService.getMappingGivenChargeNumberOrNull(it.chargeNumber)?.adjudicationNumber }
    return nomisSummary.adaSummaries.filter { it.adjudicationNumber !in dpsAdjudicationNumbers }.map { it.adjudicationNumber }
  }
}

private val dpsAdaTypes = listOf(PunishmentDto.Type.ADDITIONAL_DAYS, PunishmentDto.Type.PROSPECTIVE_DAYS)

private fun List<ReportedAdjudicationDto>.toAdaSummary(): AdaSummary {
  val adaPunishments = this.filter { it.hasValidOutcomeWithHearing() }.flatMap { it.punishments }.filter { it.type in dpsAdaTypes }
  return AdaSummary(count = adaPunishments.size, days = adaPunishments.sumOf { it.schedule.duration ?: 0 })
}

private fun ReportedAdjudicationDto.hasValidOutcomeWithHearing(): Boolean {
  // ignore outcomes that have no hearing since the punishment will not be valid
  // This matches DPS Adjustment logic
  return this.outcomes.lastOrNull()?.hearing != null
}

private fun AdjudicationADAAwardSummaryResponse.toAdaSummary(): AdaSummary = AdaSummary(count = this.adaSummaries.size, days = this.adaSummaries.sumOf { it.days })

data class MismatchAdjudicationAdaPunishments(
  val prisonerId: PrisonerIds,
  val dpsAdas: AdaSummary,
  val nomisAda: AdaSummary,
)

data class AdaSummary(
  val count: Int = 0,
  val days: Int = 0,
)
