package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

/**
 * Which prisoners a reconciliation batch covers, and how to page through them in NOMIS.
 *
 * @param label used in log messages
 * @param cursorName name of the telemetry property holding the cursor when a page fails
 */
enum class PrisonerSelection(val label: String, val cursorName: String) {
  ALL_PRISONERS("all prisoners", "offenderId") {
    override suspend fun nextPage(nomisApi: NomisApiService, last: Long, pageSize: Int) = nomisApi.getAllPrisoners(fromId = last, pageSize = pageSize)
      .let { ReconciliationSuccessPageResult(ids = it.prisonerIds.map { id -> id.offenderNo }, last = it.lastOffenderId) }
  },
  ACTIVE_PRISONERS("active prisoners", "booking") {
    override suspend fun nextPage(nomisApi: NomisApiService, last: Long, pageSize: Int) = nomisApi.getAllLatestBookings(lastBookingId = last, activeOnly = true, pageSize = pageSize)
      .let { ReconciliationSuccessPageResult(ids = it.prisonerIds.map { id -> id.offenderNo }, last = it.lastBookingId) }
  },
  ;

  abstract suspend fun nextPage(nomisApi: NomisApiService, last: Long, pageSize: Int): ReconciliationSuccessPageResult<String>
}

/**
 * @param telemetryPrefix prefix of all telemetry event names for the reconciliation
 * @param description used in log messages, e.g. "Transfer scheduler"
 */
class ReconciliationNames(val telemetryPrefix: String, val description: String)

/**
 * The mismatches found for a single prisoner; only created when there is at least one.
 */
class PrisonerMismatches<M>(val offenderNo: String, val mismatches: List<M>)

/**
 * Reconciles every prisoner in a [PrisonerSelection], emitting the standard `-requested`, `-report`,
 * `-mismatch-page-error` and `-mismatch-error` telemetry events.
 *
 * @param mismatchSummary summary of the prisoners that mismatched, added as properties to the `-report` event; defaults to
 * the first 10 offender numbers as `offenderNos`
 * @param check reconciles a single prisoner by offender number, returning the mismatches found (empty if none)
 */
class PrisonerReconciliationReport<M : Any>(
  private val telemetryClient: TelemetryClient,
  private val nomisApi: NomisApiService,
  private val names: ReconciliationNames,
  private val selection: PrisonerSelection,
  private val pageSize: Int,
  private val threadCount: Int,
  private val mismatchSummary: (List<PrisonerMismatches<M>>) -> Map<String, String> = { prisoners ->
    mapOf("offenderNos" to prisoners.map { it.offenderNo }.sorted().take(10).joinToString())
  },
  private val check: suspend (offenderNo: String) -> List<M>,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(PrisonerReconciliationReport::class.java)
  }

  suspend fun run() {
    telemetryClient.trackEvent("${names.telemetryPrefix}-requested", mapOf("TYPE" to selection.name))

    runCatching { reconcile() }
      .onSuccess {
        log.info("${names.description} ${selection.label} reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "${names.telemetryPrefix}-report",
          mapOf(
            "TYPE" to selection.name,
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + mismatchSummary(it.mismatches),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("${names.telemetryPrefix}-report", mapOf("TYPE" to selection.name, "success" to "false"))
        log.error("${names.description} ${selection.label} reconciliation report failed", it)
      }
  }

  private suspend fun reconcile(): ReconciliationResult<PrisonerMismatches<M>> = generateReconciliationReport(
    threadCount = threadCount,
    pageSize = pageSize,
    checkMatch = ::checkPrisoner,
    nextPage = ::getNextPage,
  )

  private suspend fun getNextPage(last: Long): ReconciliationPageResult<String> = runCatching { selection.nextPage(nomisApi, last, pageSize) }
    .onFailure {
      telemetryClient.trackEvent("${names.telemetryPrefix}-mismatch-page-error", mapOf(selection.cursorName to last.toString()))
      log.error("Unable to match entire page of prisoners from ${selection.cursorName}: $last", it)
    }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from ${selection.cursorName}: $last, with $pageSize prisoners") }

  private suspend fun checkPrisoner(offenderNo: String): PrisonerMismatches<M>? = runCatching {
    check(offenderNo).takeIf { it.isNotEmpty() }?.let { PrisonerMismatches(offenderNo, it) }
  }.onFailure {
    log.error("Unable to match ${names.description} for prisoner $offenderNo", it)
    telemetryClient.trackEvent(
      "${names.telemetryPrefix}-mismatch-error",
      mapOf("offenderNo" to offenderNo, "reason" to (it.message ?: "unknown")),
    )
  }.getOrNull()
}
