package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class CorePersonReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val cprCorePersonApiService: CorePersonCprApiService,
  private val nomisCorePersonApiService: CorePersonNomisApiService,
  private val nomisApiService: NomisApiService,
  @Value($$"${reports.core-person.reconciliation.page-size}")
  private val prisonerPageSize: Int = 20,
  @Value($$"${reports.prisoner.balance.reconciliation.fields:#{null}}")
  fields: String?,
) {
  private val reconciliationFields: Set<String>? = fields?.split(",")?.toSet()

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val TELEMETRY_CORE_PERSON_PREFIX = "coreperson-reports-reconciliation"
  }

  suspend fun generateReconciliationReport(activeOnly: Boolean) {
    telemetryClient.trackEvent(
      "$TELEMETRY_CORE_PERSON_PREFIX-requested",
      mapOf("activeOnly" to activeOnly.toString()),
    )
    runCatching {
      generateReconciliationReport(
        threadCount = prisonerPageSize,
        checkMatch = ::checkCorePersonMatch,
        nextPage = if (activeOnly) ::getNextActiveBookingsForPage else ::getNextAllBookingsForPage,
      )
    }
      .onSuccess {
        log.info("Core person reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_CORE_PERSON_PREFIX-report",
          mapOf(
            "activeOnly" to activeOnly.toString(),
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) +
            it.mismatches.take(5).asPrisonerMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_CORE_PERSON_PREFIX-report", mapOf("success" to "false", "error" to (it.message ?: "unknown")))
        log.error("Core person reconciliation report failed", it)
      }
  }

  private fun List<MismatchCorePerson>.asPrisonerMap(): Map<String, String> = this.associate { it.prisonNumber to "differences5=${it.differences.keys.asSequence().take(5).joinToString()}" }

  private suspend fun getNextActiveBookingsForPage(lastBookingId: Long): ReconciliationPageResult<PrisonerIds> = nextBookingsForPage(lastBookingId, activeOnly = true)

  private suspend fun getNextAllBookingsForPage(lastBookingId: Long): ReconciliationPageResult<PrisonerIds> = nextBookingsForPage(lastBookingId, activeOnly = false)

  private suspend fun nextBookingsForPage(lastBookingId: Long, activeOnly: Boolean): ReconciliationPageResult<PrisonerIds> = runCatching {
    nomisApiService.getAllLatestBookings(
      lastBookingId = lastBookingId,
      activeOnly = activeOnly,
      pageSize = prisonerPageSize,
    )
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_CORE_PERSON_PREFIX-mismatch-page-error",
      mapOf(
        "booking" to lastBookingId.toString(),
      ),
    )
    log.error("Unable to match entire page of bookings from booking: $lastBookingId", it)
  }
    .map {
      ReconciliationSuccessPageResult(
        ids = it.prisonerIds,
        last = it.lastBookingId,
      )
    }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from booking: $lastBookingId, with $prisonerPageSize bookings") }

  suspend fun checkCorePersonMatch(prisonerId: PrisonerIds): MismatchCorePerson? = runCatching {
    val (nomisCorePerson, cprCorePerson) = withContext(Dispatchers.Unconfined) {
      async { nomisCorePersonApiService.getPrisoner(prisonerId.offenderNo)?.toPerson() ?: PrisonerPerson() } to
        async { cprCorePersonApiService.getCorePerson(prisonerId.offenderNo)?.toPerson() ?: PrisonerPerson() }
    }.awaitBoth()

    return findDifferences(prisonerId, nomisCorePerson, cprCorePerson)
  }.onFailure { e ->
    log.error("Unable to match core person for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId}", e)
    telemetryClient.trackEvent(
      "$TELEMETRY_CORE_PERSON_PREFIX-mismatch-error",
      telemetryOf(
        "prisonNumber" to prisonerId.offenderNo,
        "error" to "${e.message}",
      ).also { telemetry ->
        // booking will be 0 if reconciliation is run for a single prisoner, in which case ignore
        prisonerId.bookingId.takeIf { it != 0L }?.let { telemetry["bookingId"] = it }
      },
    )
  }.getOrNull()

  private fun findDifferences(
    prisonerId: PrisonerIds,
    nomisCorePerson: PrisonerPerson,
    cprCorePerson: PrisonerPerson,
  ): MismatchCorePerson? {
    val differences = mutableMapOf<String, String>()

    appendDifference(nomisCorePerson.nationality, cprCorePerson.nationality, differences, "nationality")
    // This should be comparing the code, but core person only currently holds the description (even in code field)
    appendDifference(nomisCorePerson.religion, cprCorePerson.religion, differences, "religion")

    return differences.takeIf { it.isNotEmpty() }?.let { MismatchCorePerson(prisonNumber = prisonerId.offenderNo, differences = it) }?.also { mismatch ->
      log.info("CorePerson mismatch found {}", mismatch)
      telemetryClient.trackEvent(
        "$TELEMETRY_CORE_PERSON_PREFIX-mismatch",
        telemetryOf(
          "prisonNumber" to mismatch.prisonNumber,
        ).also { telemetry ->
          // only put the first 5 differences into telemetry
          telemetry["differences5"] = differences.keys.asSequence().take(5).joinToString()
          // booking will be 0 if reconciliation is run for a single prisoner, in which case ignore
          prisonerId.bookingId.takeIf { it != 0L }?.let { telemetry["bookingId"] = it }
        },
      )
    }
  }

  private fun appendDifference(
    nomisField: String?,
    cprField: String?,
    differences: MutableMap<String, String>,
    fieldName: String,
  ) {
    if (reconciliationFields != null && !reconciliationFields.contains(fieldName)) return
    if (nomisField != cprField) differences[fieldName] = "nomis=$nomisField, cpr=$cprField"
  }

  suspend fun checkCorePersonMatch(offenderNo: String): MismatchCorePerson? = checkCorePersonMatch(PrisonerIds(0, offenderNo))
}

fun CanonicalRecord.toPerson() = PrisonerPerson(
  nationality = nationalities.firstOrNull()?.code,
  religion = religion.description,
)
fun CorePerson.toPerson() = PrisonerPerson(
  nationality = this.nationalities?.firstOrNull()?.takeIf { it.latestBooking }?.nationality?.code,
  religion = this.beliefs?.firstOrNull()?.belief?.description,
)

data class MismatchCorePerson(
  val prisonNumber: String,
  val differences: Map<String, String>,
)

data class PrisonerPerson(
  val nationality: String? = null,
  val religion: String? = null,
)
