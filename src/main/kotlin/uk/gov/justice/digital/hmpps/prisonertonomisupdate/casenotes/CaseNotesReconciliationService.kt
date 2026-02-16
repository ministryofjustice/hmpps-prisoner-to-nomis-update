package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.google.common.base.Utf8
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Objects

const val SEE_DPS_REPLACEMENT = "... see DPS for full text"
private const val MAX_CASENOTE_LENGTH_BYTES: Int = 4000

@Service
class CaseNotesReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val caseNotesDpsApiService: CaseNotesDpsApiService,
  private val caseNotesNomisApiService: CaseNotesNomisApiService,
  private val nomisApiService: NomisApiService,
  private val caseNotesMappingApiService: CaseNotesMappingApiService,
  @Value($$"${reports.casenotes.reconciliation.page-size}")
  private val pageSize: Int = 20,
  @Value($$"${reports.casenotes.reconciliation.retry-delay-ms}")
  private val retryDelay: Int = 20,
) {
  internal companion object {
    internal val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(activeOnly: Boolean) {
    val prisonersCount = if (activeOnly) {
      nomisApiService.getActivePrisoners(0, 1).totalElements
    } else {
      nomisApiService.getAllPrisonersPaged(0, 1).totalElements
    }

    telemetryClient.trackEvent(
      "casenotes-reports-reconciliation-requested",
      mapOf("casenotes-nomis-total" to prisonersCount.toString(), "activeOnly" to activeOnly.toString()),
    )
    log.info("casenotes reconciliation report requested for $prisonersCount prisoners")

    runCatching { generateReconciliationReport(prisonersCount, activeOnly) }
      .onSuccess {
        log.info("Casenotes reconciliation report completed with ${it.size} mismatches")
        telemetryClient.trackEvent(
          "casenotes-reports-reconciliation-report",
          mapOf("mismatch-count" to it.size.toString(), "success" to "true"),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("casenotes-reports-reconciliation-report", mapOf("success" to "false", "error" to (it.message ?: "")))
        log.error("Casenotes reconciliation report failed", it)
      }
  }

  suspend fun generateReconciliationReport(prisonerCount: Long, activeOnly: Boolean): List<MismatchCaseNote> {
    if (activeOnly) {
      return prisonerCount.asPages(pageSize.toLong()).flatMap { page ->
        val prisoners = getAllPrisonersForPage(page)

        withContext(Dispatchers.Unconfined) {
          prisoners.map { prisonerId -> async { checkMatch(prisonerId) } }
        }
          .awaitAll().filterNotNull()
      }
    } else {
      val results = mutableListOf<MismatchCaseNote>()
      var last: Long = 0
      var size = 0
      var pageNumber = 0
      var pageErrors = 0
      do {
        runCatching {
          results.addAll(
            nomisApiService.getAllPrisoners(last, pageSize).let {
              size = it.prisonerIds.size
              log.info("Page $pageNumber requested from id $last, with $size prisoners")
              last = it.lastOffenderId
              pageNumber++

              withContext(Dispatchers.Unconfined) {
                it.prisonerIds.map { async { checkMatch(it) } }
              }.awaitAll().filterNotNull()
            },
          )
        }
          .onFailure {
            pageErrors++
            telemetryClient.trackEvent(
              "casenotes-reports-reconciliation-mismatch-page-error",
              mapOf(
                "page" to pageNumber.toString(),
                "pageErrors" to pageErrors.toString(),
              ),
            )
            log.error("Unable to match entire page of prisoners: page $pageNumber, pageErrors $pageErrors", it)
          }.getOrNull()
      } while (size == pageSize && pageErrors < 100)

      if (pageErrors >= 100) {
        throw RuntimeException("Aborted: Too many page errors, at page $pageNumber")
      }
      return results
    }
  }

  internal suspend fun getAllPrisonersForPage(page: Pair<Long, Long>) = runCatching {
    nomisApiService.getActivePrisoners(page.first, page.second)
      .content.map { PrisonerId(it.offenderNo) }
  }
    .onFailure {
      telemetryClient.trackEvent(
        "casenotes-reports-reconciliation-mismatch-page-error",
        mapOf(
          "page" to page.first.toString(),
        ),
      )
      log.error("Unable to match entire page of prisoners: $page", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("Page requested: $page, with ${it.size} prisoners") }

  suspend fun checkMatch(prisonerId: PrisonerId): MismatchCaseNote? {
    val offenderNo = prisonerId.offenderNo

    return runCatching {
      checkMatchOrThrowException(offenderNo)
    }.onFailure {
      log.error("Unable to match casenotes for prisoner $offenderNo", it)
      telemetryClient.trackEvent(
        "casenotes-reports-reconciliation-mismatch-error",
        mapOf(
          "offenderNo" to offenderNo,
          "error" to (it.message ?: ""),
        ),
      )
    }.getOrNull()
  }

  private data class PrisonerCaseNotes(
    var mappings: List<CaseNoteMappingDto>,
    var mappingsDpsDistinctIds: List<String>,
    var dpsCaseNotes: Map<String, ComparisonCaseNote>,
    var nomisCaseNotes: Map<Long, ComparisonCaseNote>,
  )

  /**
   * It is possible that a casenote may be being created at the same time as it is being checked here.
   * So a size discrepancy could be due to this. We avoid this by just waiting and retrying the comparison later
   */
  private suspend fun getDataWithRetry(offenderNo: String): PrisonerCaseNotes {
    var mappings = caseNotesMappingApiService.getByPrisoner(offenderNo).mappings
    var mappingsDpsDistinctIds = mappings.map { it.dpsCaseNoteId }.distinct()
    var dpsCaseNotes = caseNotesDpsApiService.getCaseNotesForPrisoner(offenderNo)

    if (mappingsDpsDistinctIds.size != dpsCaseNotes.size) {
      log.warn("getDataWithRetry() retrying after $retryDelay ms: size of mappingsDpsDistinctIds=${mappingsDpsDistinctIds.size}, dpsCaseNotes=${dpsCaseNotes.size}")
      delay(retryDelay.toLong())
      mappings = caseNotesMappingApiService.getByPrisoner(offenderNo).mappings
      mappingsDpsDistinctIds = mappings.map { it.dpsCaseNoteId }.distinct()
      dpsCaseNotes = caseNotesDpsApiService.getCaseNotesForPrisoner(offenderNo)
    }

    var nomisCaseNotes = caseNotesNomisApiService.getCaseNotesForPrisoner(offenderNo).caseNotes

    if (mappings.size != nomisCaseNotes.size) {
      log.warn("getDataWithRetry() retrying after $retryDelay ms: size of mappings=${mappings.size}, nomisCaseNotes=${nomisCaseNotes.size}")
      delay(retryDelay.toLong())
      mappings = caseNotesMappingApiService.getByPrisoner(offenderNo).mappings
      mappingsDpsDistinctIds = mappings.map { it.dpsCaseNoteId }.distinct()
      dpsCaseNotes = caseNotesDpsApiService.getCaseNotesForPrisoner(offenderNo)
      nomisCaseNotes = caseNotesNomisApiService.getCaseNotesForPrisoner(offenderNo).caseNotes
    }

    return PrisonerCaseNotes(
      mappings,
      mappingsDpsDistinctIds,
      dpsCaseNotes.associate { it.caseNoteId to it.transformFromDps() },
      nomisCaseNotes.associate { it.caseNoteId to it.transformFromNomis() },
    )
  }

  suspend fun checkMatchOrThrowException(offenderNo: String): MismatchCaseNote? {
    val (mappings, mappingsDpsDistinctIds, dpsCaseNotes, nomisCaseNotes) = getDataWithRetry(offenderNo)

    val message = "mappings.size = ${mappings.size}, mappingsDpsDistinctIds.size = ${mappingsDpsDistinctIds.size}, nomisCaseNotes.size = ${nomisCaseNotes.size}, dpsCaseNotes.size = ${dpsCaseNotes.size}"

    if (mappings.size != nomisCaseNotes.size) {
      log.info("prisoner $offenderNo : $message")
      val differences = mappings.map { it.nomisCaseNoteId }.toSet().xor(nomisCaseNotes.map { it.key }.toSet())
      telemetryClient.trackEvent(
        "casenotes-reports-reconciliation-mismatch-size-nomis",
        mapOf("offenderNo" to offenderNo, "message" to message, "differences" to differences.joinToString(",")),
      )

      checkForNomisDuplicateAndDelete(offenderNo, mappings, nomisCaseNotes, dpsCaseNotes)

      return MismatchCaseNote(offenderNo, diffsForNomis = differences, notes = listOf(message))
    }

    if (mappingsDpsDistinctIds.size != dpsCaseNotes.size) {
      log.info("prisoner $offenderNo : $message")
      val differences = mappingsDpsDistinctIds.toSet().xor(dpsCaseNotes.map { it.key }.toSet())
      telemetryClient.trackEvent(
        "casenotes-reports-reconciliation-mismatch-size-dps",
        mapOf("offenderNo" to offenderNo, "message" to message, "differences" to differences.joinToString(",")),
      )
      return MismatchCaseNote(offenderNo, diffsForDps = differences, notes = listOf(message))
    }

    val notes = mappings.mapNotNull {
      val dpsCaseNote = dpsCaseNotes[it.dpsCaseNoteId]
      val nomisCaseNote = nomisCaseNotes[it.nomisCaseNoteId]
      if (dpsCaseNote != nomisCaseNote) {
        "dpsCaseNote = $dpsCaseNote, nomisCaseNote = $nomisCaseNote"
          .also { log.info("prisoner $offenderNo : $it") }
      } else {
        null
      }
    }
    return if (notes.isEmpty()) {
      null
    } else {
      telemetryClient.trackEvent(
        "casenotes-reports-reconciliation-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
        ) + notes.withIndex().associateBy({ (it.index + 1).toString() }, { it.value }),
      )
      MismatchCaseNote(offenderNo, notes = notes)
    }
  }

  private val maxLengthToCompare = MAX_CASENOTE_LENGTH_BYTES - SEE_DPS_REPLACEMENT.length

  /**
   * There could be a duplicate created in Nomis due to a call to nomis-api succeeding in creating
   * a CN but failing to return success to to-nomis. In the specific case of there being
   * - 1 more nomis case notes than mappings,
   * - 2 identical CNs in Nomis,
   * - but only one CN in DPS with matching details,
   * - and one of the 2 not in the mapping table,
   * we fix by deleting it from Nomis
   */
  internal suspend fun checkForNomisDuplicateAndDelete(
    offenderNo: String,
    mappings: List<CaseNoteMappingDto>,
    nomisCaseNotes: Map<Long, ComparisonCaseNote>,
    dpsCaseNotes: Map<String, ComparisonCaseNote>,
  ) {
    if (mappings.size + 1 == nomisCaseNotes.size) {
      //
      nomisCaseNotes.values
        .groupBy { it.text.take(maxLengthToCompare) + it.occurrenceDateTime + it.creationDateTime + it.type + it.subType }
        .filter { it.value.size == 2 }
        .forEach { (_, identicalCaseNotePair) ->
          val nomisCaseNote1 = identicalCaseNotePair.first()
          val nomisCaseNote2 = identicalCaseNotePair.last()
          val textToCompare = nomisCaseNote1.text.take(maxLengthToCompare)
          val exactlyOneExistsInDps = dpsCaseNotes.values.filter {
            it.text.take(maxLengthToCompare) == textToCompare &&
              it.occurrenceDateTime == nomisCaseNote1.occurrenceDateTime &&
              it.creationDateTime == nomisCaseNote1.creationDateTime &&
              it.type == nomisCaseNote1.type &&
              it.subType == nomisCaseNote1.subType
          }
            .size == 1

          if (exactlyOneExistsInDps) {
            val inMapping1 = mappings.any { it.nomisCaseNoteId == nomisCaseNote1.legacyId }
            val inMapping2 = mappings.any { it.nomisCaseNoteId == nomisCaseNote2.legacyId }
            if (inMapping1 && !inMapping2) {
              caseNotesNomisApiService.deleteCaseNote(nomisCaseNote2.legacyId)
              telemetryClient.trackEvent(
                "casenotes-reports-reconciliation-mismatch-deleted",
                mapOf("offenderNo" to offenderNo, "nomisId" to nomisCaseNote2.id),
              )
            } else if (!inMapping1 && inMapping2) {
              caseNotesNomisApiService.deleteCaseNote(nomisCaseNote1.legacyId)
              telemetryClient.trackEvent(
                "casenotes-reports-reconciliation-mismatch-deleted",
                mapOf("offenderNo" to offenderNo, "nomisId" to nomisCaseNote1.id),
              )
            }
          }
        }
    }
  }

  private val truncatedToSecondsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
  private val amendmentFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")

  private fun CaseNote.transformFromDps(): ComparisonCaseNote {
    val sortedAmendments = amendments.map { a ->
      CommonAmendmentFields(
        text = a.additionalNoteText,
        occurrenceDateTime = a.creationDateTime?.format(amendmentFormatter),
        authorUsername = a.authorUserName,
      )
    }.toSortedSet(amendmentComparator)
    return ComparisonCaseNote(
      id = caseNoteId,
      text = (text + sortedAmendments.joinToString("") { it.toNomisAmendment() })
        .truncateToFitNomis(getLastModified()),
      type = type,
      subType = subType,
      occurrenceDateTime = occurrenceDateTime.format(truncatedToSecondsFormatter),
      creationDateTime = creationDateTime.format(truncatedToSecondsFormatter),
      dpsUsername = authorUsername,
      legacyId = legacyId,
    )
  }

  private fun CaseNoteResponse.transformFromNomis(): ComparisonCaseNote {
    val sortedAmendments = amendments.map { a ->
      CommonAmendmentFields(
        text = a.text,
        occurrenceDateTime = a.createdDateTime.format(amendmentFormatter),
        authorUsername = a.authorUsername,
      )
    }.toSortedSet(amendmentComparator)

    return ComparisonCaseNote(
      id = caseNoteId.toString(),
      text = caseNoteText + sortedAmendments.joinToString("") { it.toNomisAmendment() },
      type = caseNoteType.code,
      subType = caseNoteSubType.code,
      occurrenceDateTime = occurrenceDateTime?.format(truncatedToSecondsFormatter),
      creationDateTime = creationDateTime?.format(truncatedToSecondsFormatter),
      nomisUsernames = authorUsernames,
      legacyId = caseNoteId,
    )
  }
}

private val algorithmChangeDate = LocalDateTime.parse("2025-07-17T08:08:00")

private fun String.truncateToFitNomis(modifiedDateTime: LocalDateTime): String = if (
  modifiedDateTime.isBefore(algorithmChangeDate)
) {
  this.truncateToFixNomisMaxLength()
} else {
  this.truncateToUtf8Length(MAX_CASENOTE_LENGTH_BYTES, true)
}

// same OLD logic as in nomis prisoner api (pre 17/7/2025 08:08)
private fun String.truncateToFixNomisMaxLength(): String = // encodedLength always >= length
  if (Utf8.encodedLength(this) <= MAX_CASENOTE_LENGTH_BYTES) {
    this
  } else {
    substring(0, MAX_CASENOTE_LENGTH_BYTES - (Utf8.encodedLength(this) - length) - SEE_DPS_REPLACEMENT.length) + SEE_DPS_REPLACEMENT
  }

// NEW logic. NOTE: code copied from nomis prisoner api StringUtils
fun String.truncateToUtf8Length(maxLength: Int, includeSeeDpsSuffix: Boolean = false): String {
  // ensure doesn't exceed the number of bytes Oracle can take - allowing for suffix to be added

  if (Utf8.encodedLength(this) <= maxLength) {
    return this
  }

  var truncated: String = this
  val suffix = if (includeSeeDpsSuffix) SEE_DPS_REPLACEMENT else ""
  val checkLength = maxLength - suffix.length

  // so we don't cut into a double/triple byte character while truncating check that
  // we still have a valid string before returning, even if it fits the number of bytes
  while (!truncated.isStillValid() || Utf8.encodedLength(truncated) > checkLength) {
    // Keep knocking off the last character until it fits the number of bytes and remains a valid string
    truncated = truncated.take(truncated.length - 1)
  }
  return truncated + suffix
}

// Utf8.encodedLength will throw if the resulting String is cut at the incorrect boundary
private fun String.isStillValid(): Boolean = runCatching { Utf8.encodedLength(this) }.map { true }.getOrDefault(false)

internal fun CaseNote.getLastModified(): LocalDateTime = amendments
  .reduceOrNull { acc, cur ->
    val cdt = cur.creationDateTime
    if (cdt != null && cdt.isAfter(acc.creationDateTime)) {
      cur
    } else {
      acc
    }
  }
  ?.creationDateTime
  ?: creationDateTime

data class MismatchCaseNote(
  val offenderNo: String,
  val diffsForDps: Set<String> = emptySet(),
  val diffsForNomis: Set<Long> = emptySet(),
  val notes: List<String> = emptyList(),
)

data class ComparisonCaseNote(
  val id: String,
  val text: String,
  val type: String,
  val subType: String,
  val occurrenceDateTime: String?,
  val creationDateTime: String?,
  val dpsUsername: String? = null,
  val nomisUsernames: List<String>? = null,
  val legacyId: Long,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ComparisonCaseNote) return false
    return text == other.text &&
      equalTypes(other) &&
      subType == other.subType &&
      occurrenceDateTime == other.occurrenceDateTime &&
      creationDateTime == other.creationDateTime &&
      equalUsers(other)
  }

  private fun equalTypes(nomis: ComparisonCaseNote): Boolean = type == nomis.type || (type == "APP" && nomis.type == "CNOTE" && subType == "OUTCOME")

  private fun equalUsers(nomis: ComparisonCaseNote): Boolean {
    val equal = nomis.nomisUsernames?.contains(dpsUsername) == true || dpsUsername == "OMS_OWNER"
    if (!equal) {
      CaseNotesReconciliationService.log.info("authorUsername not equal: $dpsUsername !in ${nomis.nomisUsernames}")
    }
    return equal
  }

  override fun hashCode(): Int = javaClass.hashCode()

  override fun toString(): String = "{id=$id, legacyId=$legacyId, text-hash=${Objects.hashCode(text)}, type=$type, subType=$subType, occurrenceDateTime=$occurrenceDateTime, creationDateTime=$creationDateTime, authorUsername=${dpsUsername ?: nomisUsernames}}"
}

data class CommonAmendmentFields(
  val text: String,
  val occurrenceDateTime: String?,
  val authorUsername: String,
) {
  override fun toString(): String = "{text-hash=${Objects.hashCode(text)}, occurrenceDateTime=$occurrenceDateTime, authorUsername=$authorUsername}"
  fun toNomisAmendment(): String = " ...[$authorUsername updated the case notes on $occurrenceDateTime] $text"
}

private val amendmentComparator = compareBy(
  CommonAmendmentFields::occurrenceDateTime,
  CommonAmendmentFields::text,
  CommonAmendmentFields::authorUsername,
)

// calculate the exclusive or (aka symmetric difference or disjunctive union) of two sets
infix fun <T> Set<T>.xor(that: Set<T>): Set<T> = (this - that) + (that - this)
