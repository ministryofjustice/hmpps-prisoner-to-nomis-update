package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@OptIn(ExperimentalCoroutinesApi::class)
@Service
class PrisonerRestrictionsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: ContactPersonNomisApiService,
  private val mappingApiService: ContactPersonMappingApiService,
  private val dpsApiService: ContactPersonDpsApiService,

  @param:Value($$"${reports.contact-person.prisoner-restrictions.reconciliation.page-size:10}") private val pageSize: Int = 10,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_PRISONER_PREFIX = "contact-person-prisoner-restriction-reconciliation"
  }

  suspend fun generatePrisonerRestrictionsReconciliationReport(): ReconciliationResult<MismatchPrisonerRestriction> {
    // TODO add this in when DPS have a totals endpoint
    // checkTotalsMatch()

    return generateReconciliationReport(
      threadCount = pageSize,
      checkMatch = ::checkPrisonerRestrictionsMatch,
      nextPage = ::getNextRestrictionsForPage,
    )
  }

  @Suppress("unused")
  private suspend fun checkTotalsMatch() = runCatching {
    // TODO - request DPS paging service
    val (nomisTotal, dpsTotal) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getPrisonerRestrictionIdsTotals().totalElements } to
        async { nomisApiService.getPrisonerRestrictionIdsTotals().totalElements }
    }.awaitBoth()

    if (nomisTotal != dpsTotal) {
      telemetryClient.trackEvent(
        "$TELEMETRY_PRISONER_PREFIX-mismatch-totals",
        mapOf(
          "nomisTotal" to nomisTotal.toString(),
          "dpsTotal" to dpsTotal.toString(),
        ),
      )
    }
  }.onFailure {
    log.error("Unable to get prisoner restrictions totals", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_PRISONER_PREFIX-mismatch-totals-error",
      mapOf(),
    )
  }

  private suspend fun getNextRestrictionsForPage(lastRestrictionId: Long): ReconciliationPageResult<Long> = runCatching { nomisApiService.getPrisonerRestrictionIds(lastRestrictionId = lastRestrictionId, pageSize = pageSize) }
    .onFailure {
      telemetryClient.trackEvent(
        "$TELEMETRY_PRISONER_PREFIX-mismatch-page-error",
        mapOf(
          "restriction" to lastRestrictionId.toString(),
        ),
      )
      log.error("Unable to match entire page of restrictions from restriction: $lastRestrictionId", it)
    }
    .map { ReconciliationSuccessPageResult(ids = it.restrictionIds, last = it.lastRestrictionId) }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from person: $lastRestrictionId, with $pageSize restriction") }

  suspend fun checkPrisonerRestrictionsMatch(restrictionId: Long): MismatchPrisonerRestriction? {
    val (_, dpsRestriction) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getPrisonerRestrictionById(restrictionId) } to
        async {
          mappingApiService.getByNomisPrisonerRestrictionIdOrNull(restrictionId)?.let {
            dpsApiService.getPrisonerRestriction(it.dpsId.toLong())
          }
        }
    }.awaitBoth()

    if (dpsRestriction == null) {
      telemetryClient.trackEvent(
        "$TELEMETRY_PRISONER_PREFIX-mismatch",
        mapOf(
          "nomisRestrictionId" to restrictionId.toString(),
          "reason" to "restriction-mapping-missing",
        ),
      )
      return MismatchPrisonerRestriction(restrictionId = restrictionId)
    }

    return null
  }
}

data class MismatchPrisonerRestriction(
  val restrictionId: Long,
)
