package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationReconciliationService.Companion.TELEMETRY_PERSON_LOCATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationReconciliationService.Companion.log
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@Service
class PersonLocationReconciliationServiceActivePrisoners(
  private val telemetryClient: TelemetryClient,
  private val prisonerApi: NomisApiService,
  private val reconciliationService: PersonLocationReconciliationService,
  @param:Value($$"${reports.person-location.active-prisoners.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.person-location.active-prisoners.reconciliation.thread-count}") private val threadCount: Int = 15,
) {

  suspend fun generatePersonLocationReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_PERSON_LOCATION-requested",
      mapOf("TYPE" to "ACTIVE"),
    )

    runCatching { generatePersonLocationReconciliationReport() }
      .onSuccess {
        log.info("Person location active prisoners reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_PERSON_LOCATION-report",
          mapOf(
            "TYPE" to "ACTIVE",
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_PERSON_LOCATION-report", mapOf("success" to "false"))
        log.error("Person location active prisoners reconciliation report failed", it)
      }
  }

  private fun List<PrisonerIds>.asMap(): Pair<String, String> = this
    .sortedBy { it.offenderNo }.take(10).let { mismatch -> "offenderNos" to mismatch.joinToString { it.offenderNo } }

  suspend fun generatePersonLocationReconciliationReport(): ReconciliationResult<PrisonerIds> = generateReconciliationReport(
    threadCount = threadCount,
    pageSize = pageSize,
    checkMatch = ::checkPrisonersMatch,
    nextPage = ::getNextBookingsForPage,
  )

  private suspend fun getNextBookingsForPage(lastBookingId: Long): ReconciliationPageResult<PrisonerIds> = runCatching {
    prisonerApi.getAllLatestBookings(
      lastBookingId = lastBookingId,
      activeOnly = true,
      pageSize = pageSize,
    )
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_PERSON_LOCATION-mismatch-page-error",
      mapOf(
        "booking" to lastBookingId.toString(),
      ),
    )
    log.error("Unable to match entire page of bookings from booking ID: $lastBookingId", it)
  }
    .map {
      ReconciliationSuccessPageResult(
        ids = it.prisonerIds,
        last = it.lastBookingId,
      )
    }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from booking ID: $lastBookingId, with $pageSize offenders") }

  suspend fun checkPrisonersMatch(prisonerIds: PrisonerIds): PrisonerIds? = runCatching {
    reconciliationService.checkPrisonersMatch(prisonerIds.offenderNo).takeIf { it.isNotEmpty() }
      ?.let { prisonerIds }
  }.onFailure {
    log.error("Unable to match person locations for prisoner ${prisonerIds.offenderNo}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_PERSON_LOCATION-mismatch-error",
      mapOf(
        "offenderNo" to prisonerIds.offenderNo,
        "reason" to (it.message ?: "unknown"),
      ),
    )
  }.getOrNull()
}
