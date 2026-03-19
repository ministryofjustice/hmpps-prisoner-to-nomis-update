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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonCanonicalRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligionGet
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CorePersonReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val cprCorePersonApiService: CorePersonCprApiService,
  private val nomisCorePersonApiService: CorePersonNomisApiService,
  private val nomisApiService: NomisApiService,
  @Value($$"${reports.core-person.reconciliation.page-size}")
  private val prisonerPageSize: Int = 20,
  @Value($$"${reports.core-person.reconciliation.fields:#{null}}")
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
    appendReligionsDifference(nomisCorePerson.religions, cprCorePerson.religions, differences, "religions")

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

  private fun appendReligionsDifference(
    nomisField: List<PrisonerReligion>,
    cprField: List<PrisonerReligion>,
    differences: MutableMap<String, String>,
    fieldName: String,
  ) {
    if (reconciliationFields != null && !reconciliationFields.contains(fieldName)) return
    if (nomisField.size != cprField.size) {
      differences[fieldName] = "nomis=${nomisField.size}, cpr=${cprField.size}"
    } else {
      nomisField.mapIndexedNotNull { i, n ->
        val cpr = cprField[i]
        if (n.religion != cpr.religion) {
          "$i-code:nomis=${n.religion}, cpr=${cpr.religion}"
        } else if (n.comments != cpr.comments) {
          "$i-comments:nomis=${n.comments}, cpr=${cpr.comments}"
        } else if (n.startDate?.equals(cpr.startDate) == false) {
          "$i-startDate:nomis=${n.startDate}, cpr=${cpr.startDate}"
        } else if (n.endDate?.equals(cpr.endDate) == false) {
          "$i-endDate:nomis=${n.endDate}, cpr=${cpr.endDate}"
        } else if (n.current != cpr.current) {
          "$i-current:nomis=${n.current}, cpr=${cpr.current}"
        } else if (n.createUsername != cpr.createUsername) {
          "$i-createUser:nomis=${n.createUsername}, cpr=${cpr.createUsername}"
        } else if (!n.createDatetime.equals(cpr.createDatetime)) {
          "$i-createDatetime:nomis=${n.createDatetime}, cpr=${cpr.createDatetime}"
        } else if (n.modifyUsername != cpr.modifyUsername) {
          "$i-modifyUser:nomis=${n.modifyUsername}, cpr=${cpr.modifyUsername}"
        } else if (n.modifyDatetime?.equals(cpr.modifyDatetime) ?: false) {
          "$i-modifyDatetime:nomis=${n.modifyDatetime}, cpr=${cpr.modifyDatetime}"
        } else {
          null
        }
      }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ",")
        ?.apply { differences[fieldName] = this }
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

fun PrisonCanonicalRecord.toPerson() = PrisonerPerson(
  nationality = nationalities.firstOrNull()?.code,
  religion = religion.description,
  // TODO: remove sorting here once core person implement sorting properly their end
  religions = religionHistory.sortedByDescending(PrisonReligionGet::startDate).map {
    PrisonerReligion(
      religion = it.religionCode,
      startDate = it.startDate,
      endDate = it.endDate,
      current = it.current,
      comments = it.comments,
      createUsername = it.createUserId,
      createDatetime = it.createDateTime,
      modifyUsername = it.modifyUserId,
      modifyDatetime = it.modifyDateTime,
    )
  },
)
fun CorePerson.toPerson() = PrisonerPerson(
  nationality = this.nationalities?.firstOrNull()?.takeIf { it.latestBooking }?.nationality?.code,
  religion = this.beliefs?.firstOrNull()?.belief?.description,
  religions = this.beliefs?.mapIndexed { i, r ->
    PrisonerReligion(
      religion = r.belief.code,
      startDate = r.startDate,
      endDate = r.endDate,
      current = i == 0,
      comments = r.comments,
      createUsername = r.audit.createUsername,
      createDatetime = r.audit.createDatetime,
      modifyUsername = r.audit.modifyUserId,
      modifyDatetime = r.audit.modifyDatetime,
    )
  } ?: emptyList(),
)

data class MismatchCorePerson(
  val prisonNumber: String,
  val differences: Map<String, String>,
)

data class PrisonerPerson(
  val nationality: String? = null,
  val religion: String? = null,
  val religions: List<PrisonerReligion> = emptyList(),
)

data class PrisonerReligion(
  val religion: String?,
  val startDate: LocalDate?,
  val endDate: LocalDate?,
  val current: Boolean?,
  val comments: String?,
  val createUsername: String,
  val createDatetime: LocalDateTime,
  val modifyUsername: String?,
  val modifyDatetime: LocalDateTime?,
)
