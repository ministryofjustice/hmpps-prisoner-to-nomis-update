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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AdjudicationADAAwardSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ActivePrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages

@Service
class AdjudicationsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val adjudicationsApiService: AdjudicationsApiService,
  @Value("\${reports.sentencing.reconciliation.page-size}")
  private val pageSize: Long = 20,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(activePrisonersCount: Long): List<MismatchAdjudicationAdaPunishments> {
    return activePrisonersCount.asPages(pageSize).flatMap { page ->
      val activePrisoners = getActivePrisonersForPage(page)

      withContext(Dispatchers.Unconfined) {
        activePrisoners.map { async { checkADAPunishmentsMatch(it) } }
      }.awaitAll().filterNotNull()
    }
  }

  private suspend fun getActivePrisonersForPage(page: Pair<Long, Long>) =
    runCatching { nomisApiService.getActivePrisoners(page.first, page.second).content }
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

  suspend fun checkADAPunishmentsMatch(prisonerId: ActivePrisonerId): MismatchAdjudicationAdaPunishments? = runCatching {
    val nomisSummary = nomisApiService.getAdaAwardsSummary(prisonerId.bookingId)
    val dpsAdjudications = adjudicationsApiService.getAdjudicationsByBookingId(prisonerId.bookingId, nomisSummary.prisonIds)

    val nomisAdaSummary: AdaSummary = nomisSummary.toAdaSummary()
    val dpsAdaSummary: AdaSummary = dpsAdjudications.toAdaSummary()
    return if (nomisAdaSummary != dpsAdaSummary) MismatchAdjudicationAdaPunishments(prisonerId = prisonerId, dpsAdas = dpsAdaSummary, nomisAda = nomisAdaSummary) else null
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
}

private val dpsAdaTypes = listOf(PunishmentDto.Type.ADDITIONAL_DAYS, PunishmentDto.Type.PROSPECTIVE_DAYS)

private fun List<ReportedAdjudicationDto>.toAdaSummary(): AdaSummary {
  val adaPunishments = this.flatMap { it.punishments }.filter { it.type in dpsAdaTypes }
  return AdaSummary(count = adaPunishments.size, days = adaPunishments.sumOf { it.schedule.days })
}

private fun AdjudicationADAAwardSummaryResponse.toAdaSummary(): AdaSummary {
  return AdaSummary(count = this.adaSummaries.size, days = this.adaSummaries.sumOf { it.days })
}

data class MismatchAdjudicationAdaPunishments(
  val prisonerId: ActivePrisonerId,
  val dpsAdas: AdaSummary,
  val nomisAda: AdaSummary,
)

data class AdaSummary(
  val count: Int = 0,
  val days: Int = 0,
)
