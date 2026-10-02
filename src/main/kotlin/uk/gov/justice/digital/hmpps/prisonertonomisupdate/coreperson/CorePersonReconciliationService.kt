@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEventOrSuppress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.DpsPrisonRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Objects

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
  private val reconciliationFields: Set<String> = fields?.split(",")?.toSet() ?: emptySet()

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val TELEMETRY_CORE_PERSON_PREFIX = "coreperson-reports-reconciliation"

    private val excludedBookingIds = CorePersonReconciliationService::class.java
      .getResource("/excludedCorePersonReconciliationBookingId.txt")
      .readText()
      .split(",")
      .mapNotNull { it.trim().toLongOrNull() }
  }

  suspend fun generateReconciliationReport(activeOnly: Boolean) {
    telemetryClient.trackEvent(
      "$TELEMETRY_CORE_PERSON_PREFIX-requested",
      mapOf("activeOnly" to activeOnly.toString()),
    )
    runCatching {
      generateReconciliationReport(
        threadCount = prisonerPageSize,
        checkMatch = { checkCorePersonMatch(it, suppressEvents = false) },
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

  suspend fun checkCorePersonMatch(prisonerId: PrisonerIds, suppressEvents: Boolean): MismatchCorePerson? = runCatching {
    val (nomisCorePerson, cprCorePerson) = withContext(Dispatchers.Unconfined) {
      async { nomisCorePersonApiService.getPrisonerForReconciliation(prisonerId.offenderNo)?.toPerson() ?: PrisonerPerson() } to
        async { cprCorePersonApiService.getCorePerson(prisonerId.offenderNo)?.toPerson() ?: PrisonerPerson() }
    }.awaitBoth()

    return findDifferences(prisonerId, nomisCorePerson, cprCorePerson, suppressEvents)
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
    suppressEvents: Boolean,
  ): MismatchCorePerson? {
    val differences = mutableMapOf<String, String>()

    appendDifference(nomisCorePerson.religion, cprCorePerson.religion, differences, "religion")
    appendReligionsDifference(nomisCorePerson.religions, cprCorePerson.religions, differences)
    appendAddressesDifference(nomisCorePerson.addresses, cprCorePerson.addresses, differences)

    val excluded = excludedBookingIds.contains(prisonerId.bookingId)
    return if (!excluded) {
      differences.takeIf { it.isNotEmpty() }
        ?.let { MismatchCorePerson(prisonNumber = prisonerId.offenderNo, differences = it) }?.also { mismatch ->
          log.info("CorePerson mismatch found {}", mismatch)
          telemetryClient.trackEventOrSuppress(
            "$TELEMETRY_CORE_PERSON_PREFIX-mismatch",
            telemetryOf(
              "prisonNumber" to mismatch.prisonNumber,
            ).also { telemetry ->
              // only put the first 5 differences into telemetry
              telemetry["differences5"] = differences.keys.asSequence().take(5).joinToString()
              // booking will be 0 if reconciliation is run for a single prisoner, in which case ignore
              prisonerId.bookingId.takeIf { it != 0L }?.let { telemetry["bookingId"] = it }
            },
            suppressEvent = suppressEvents,
          )
        }
    } else {
      if (differences.isEmpty()) {
        telemetryClient.trackEventOrSuppress(
          "$TELEMETRY_CORE_PERSON_PREFIX-excluded-offender-resolved",
          mapOf(
            "reason" to ("No reconciliation mismatches found for excluded bookingId ${prisonerId.bookingId}. Remove from exclusion file."),
          ),
          suppressEvent = suppressEvents,
        )
      } else {
        telemetryClient.trackEventOrSuppress(
          "$TELEMETRY_CORE_PERSON_PREFIX-excluded-offender",
          mapOf(
            "reason" to ("Excluding reconciliation mismatches for bookingId ${prisonerId.bookingId}"),
          ),
          suppressEvent = suppressEvents,
        )
      }
      null
    }
  }

  private fun appendReligionsDifference(
    nomisField: List<PrisonerReligion>,
    cprField: List<PrisonerReligion>,
    differences: MutableMap<String, String>,
  ) {
    val fieldName = "religions"
    if (shouldNotReconcile(fieldName)) return
    if (nomisField.size != cprField.size) {
      differences[fieldName] = "nomis=${nomisField.size}, cpr=${cprField.size}"
    } else {
      nomisField.mapIndexedNotNull { i, n ->
        val cpr = cprField[i]
        when {
          n.religion != cpr.religion -> "$i-code:nomis=${n.religion}, cpr=${cpr.religion}"
          n.comments != cpr.comments -> "$i-comments:nomis=${n.comments}, cpr=${cpr.comments}"
          !Objects.equals(n.startDate, cpr.startDate) -> "$i-startDate:nomis=${n.startDate}, cpr=${cpr.startDate}"
          !Objects.equals(n.endDate, cpr.endDate) -> "$i-endDate:nomis=${n.endDate}, cpr=${cpr.endDate}"
          n.current != cpr.current -> "$i-current:nomis=${n.current}, cpr=${cpr.current}"
          n.createDatetime.notEqualsIgnoringNanos(cpr.createDatetime) -> "$i-createDatetime:nomis=${n.createDatetime}, cpr=${cpr.createDatetime}"
          else -> null
        }
      }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ",")
        ?.apply { differences[fieldName] = this }
    }
  }

  private fun shouldNotReconcile(fieldName: String): Boolean = !reconciliationFields.contains(fieldName)

  private fun appendAddressesDifference(
    nomisField: List<PrisonerAddress>,
    cprField: List<PrisonerAddress>,
    differences: MutableMap<String, String>,
  ) {
    val fieldName = "addresses"
    if (shouldNotReconcile(fieldName)) return
    if (nomisField.size != cprField.size) {
      differences[fieldName] = "nomis=${nomisField.size}, cpr=${cprField.size}"
    } else {
      nomisField.mapIndexedNotNull { i, n ->
        val cpr = cprField[i]
        when {
          n.noFixedAbode != cpr.noFixedAbode -> "$i-noFixedAbode:nomis=${n.noFixedAbode}, cpr=${cpr.noFixedAbode}"
          !Objects.equals(n.startDate, cpr.startDate) -> "$i-startDate:nomis=${n.startDate}, cpr=${cpr.startDate}"
          !Objects.equals(n.endDate, cpr.endDate) -> "$i-endDate:nomis=${n.endDate}, cpr=${cpr.endDate}"
          n.postcode != cpr.postcode -> "$i-postcode:nomis=${n.postcode}, cpr=${cpr.postcode}"
          n.subBuildingName != cpr.subBuildingName -> "$i-subBuildingName:nomis=${n.subBuildingName}, cpr=${cpr.subBuildingName}"
          n.buildingNumber != cpr.buildingNumber -> "$i-buildingNumber:nomis=${n.buildingNumber}, cpr=${cpr.buildingNumber}"
          n.thoroughfareName != cpr.thoroughfareName -> "$i-thoroughfareName:nomis=${n.thoroughfareName}, cpr=${cpr.thoroughfareName}"
          n.dependentLocality != cpr.dependentLocality -> "$i-dependentLocality:nomis=${n.dependentLocality}, cpr=${cpr.dependentLocality}"
          n.postTown != cpr.postTown -> "$i-postTown:nomis=${n.postTown}, cpr=${cpr.postTown}"
          n.county != cpr.county -> "$i-county:nomis=${n.county}, cpr=${cpr.county}"
          n.countryCode != cpr.countryCode -> "$i-countryCode:nomis=${n.countryCode}, cpr=${cpr.countryCode}"
          n.comment != cpr.comment -> "$i-comment:nomis=${n.comment}, cpr=${cpr.comment}"
          else -> null
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
    if (shouldNotReconcile(fieldName)) return
    if (nomisField != cprField) differences[fieldName] = "nomis=$nomisField, cpr=$cprField"
  }

  suspend fun checkCorePersonMatch(offenderNo: String, suppressEvents: Boolean = false): MismatchCorePerson? = checkCorePersonMatch(PrisonerIds(0, offenderNo), suppressEvents)
}

private fun LocalDateTime?.notEqualsIgnoringNanos(createDatetime: LocalDateTime?): Boolean = !Objects.equals(this?.withNano(0), createDatetime?.withNano(0))

fun DpsPrisonRecord.toPerson() = PrisonerPerson(
  religion = religion.code?.name,
  religions = religionHistory.map {
    PrisonerReligion(
      religion = it.religionCode.name,
      startDate = it.startDate,
      endDate = it.endDate,
      current = it.current,
      comments = it.comments,
      createDatetime = it.createDateTime,
    )
  },
  addresses = addresses.map { it.toPrisonerAddress() },
)

private fun CanonicalAddress.toPrisonerAddress() = PrisonerAddress(
  noFixedAbode = noFixedAbode,
  startDate = startDate?.let { LocalDate.parse(it) },
  endDate = endDate?.let { LocalDate.parse(it) },
  postcode = postcode,
  subBuildingName = subBuildingName,
  buildingNumber = buildingNumber,
  thoroughfareName = thoroughfareName,
  dependentLocality = dependentLocality,
  postTown = postTown,
  county = county,
  countryCode = countryCode?.value,
  comment = comment,
)

fun CorePerson.toPerson() = PrisonerPerson(
  religion = beliefs?.firstOrNull()?.belief?.code,
  religions = beliefs?.mapIndexed { i, r ->
    PrisonerReligion(
      religion = r.belief.code,
      startDate = r.startDate,
      endDate = r.endDate,
      current = i == 0,
      comments = r.comments,
      createDatetime = r.audit.createDatetime,
    )
  } ?: emptyList(),
  addresses = addresses?.map { it.toPrisonerAddress() } ?: emptyList(),
)

private fun OffenderAddress.toPrisonerAddress() = PrisonerAddress(
  noFixedAbode = noFixedAddress,
  startDate = startDate,
  endDate = endDate,
  postcode = postcode,
  subBuildingName = flat,
  buildingNumber = premise,
  thoroughfareName = street,
  dependentLocality = locality,
  postTown = city?.description,
  county = county?.description,
  countryCode = country?.code?.toCprCountryCode(),
  comment = comment,
)

// NOMIS and CPR have slightly different country codes so need to translate
private fun String.toCprCountryCode(): String = when (this) {
  "IOM" -> "IMN"
  "ROM" -> "ROU"
  else -> this
}

data class MismatchCorePerson(
  val prisonNumber: String,
  val differences: Map<String, String>,
)

data class PrisonerPerson(
  val religion: String? = null,
  val religions: List<PrisonerReligion> = emptyList(),
  val addresses: List<PrisonerAddress> = emptyList(),
)

data class PrisonerReligion(
  val religion: String?,
  val startDate: LocalDate?,
  val endDate: LocalDate?,
  val current: Boolean?,
  val comments: String?,
  val createDatetime: LocalDateTime,
)

data class PrisonerAddress(
  val noFixedAbode: Boolean?,
  val startDate: LocalDate?,
  val endDate: LocalDate?,
  val postcode: String?,
  val subBuildingName: String?,
  val buildingNumber: String?,
  val thoroughfareName: String?,
  val dependentLocality: String?,
  val postTown: String?,
  val county: String?,
  val countryCode: String?,
  val comment: String?,
)
