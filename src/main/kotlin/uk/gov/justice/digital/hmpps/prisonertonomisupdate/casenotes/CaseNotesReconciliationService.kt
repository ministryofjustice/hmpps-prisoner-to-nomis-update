package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.google.common.base.Utf8
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import java.time.format.DateTimeFormatter
import java.util.Objects

@Service
class CaseNotesReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val caseNotesDpsApiService: CaseNotesDpsApiService,
  private val caseNotesNomisApiService: CaseNotesNomisApiService,
  private val nomisApiService: NomisApiService,
  private val caseNotesMappingApiService: CaseNotesMappingApiService,
  @Value("\${reports.casenotes.reconciliation.page-size}")
  private val pageSize: Long = 20,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val seeDpsReplacement = "... see DPS for full text"
    private const val MAX_CASENOTE_LENGTH_BYTES: Int = 4000
  }

  suspend fun generateReconciliationReport(prisonerCount: Long, activeOnly: Boolean): List<MismatchCaseNote> {
    if (activeOnly) {
      return prisonerCount.asPages(pageSize).flatMap { page ->
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
            nomisApiService.getAllPrisoners(last, pageSize.toInt()).let {
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
      } while (size == pageSize.toInt() && pageErrors < 100)

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

  suspend fun checkMatchOrThrowException(offenderNo: String): MismatchCaseNote? {
    val mappings = caseNotesMappingApiService.getByPrisoner(offenderNo).mappings

    val mappingsDpsDistinctIds = mappings.map { it.dpsCaseNoteId }.distinct()

    val dpsCaseNotes = caseNotesDpsApiService.getCaseNotesForPrisoner(offenderNo)
      .associate { it.caseNoteId to it.transformFromDps() }

    val nomisCaseNotes = caseNotesNomisApiService.getCaseNotesForPrisoner(offenderNo).caseNotes
      .associate { it.caseNoteId to it.transformFromNomis() }

    val message = "mappings.size = ${mappings.size}, mappingsDpsDistinctIds.size = ${mappingsDpsDistinctIds.size}, nomisCaseNotes.size = ${nomisCaseNotes.size}, dpsCaseNotes.size = ${dpsCaseNotes.size}"

    if (mappings.size != nomisCaseNotes.size) {
      log.info("prisoner $offenderNo : $message")
      val differences = mappings.map { it.nomisCaseNoteId }.toSet().xor(nomisCaseNotes.map { it.key }.toSet())
      telemetryClient.trackEvent(
        "casenotes-reports-reconciliation-mismatch-size-nomis",
        mapOf("offenderNo" to offenderNo, "message" to message, "differences" to differences.joinToString(",")),
      )
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

  private val truncatedToSecondsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

  private fun CaseNote.transformFromDps() = CommonCaseNoteFields(
    text.truncateToFixNomisMaxLength(),
    type,
    subType,
    occurrenceDateTime.format(truncatedToSecondsFormatter),
    creationDateTime.format(truncatedToSecondsFormatter),
    authorUsername,
    null,
    amendments.map { a ->
      CommonAmendmentFields(
        text = a.additionalNoteText.truncateToFixNomisMaxLength(),
        occurrenceDateTime = a.creationDateTime?.format(truncatedToSecondsFormatter),
        authorUsername = a.authorUserName,
      )
    }.toSortedSet(amendmentComparator),
    legacyId,
  )

  private fun CaseNoteResponse.transformFromNomis() = CommonCaseNoteFields(
    caseNoteText,
    caseNoteType.code,
    caseNoteSubType.code,
    occurrenceDateTime,
    creationDateTime,
    null,
    authorUsernames,
    amendments.map { a ->
      CommonAmendmentFields(
        text = a.text,
        occurrenceDateTime = a.createdDateTime,
        authorUsername = a.authorUsername,
      )
    }.toSortedSet(amendmentComparator),
    caseNoteId,
  )

  // same logic as in nomis prisoner api
  private fun String.truncateToFixNomisMaxLength(): String = // encodedLength always >= length
    if (Utf8.encodedLength(this) <= MAX_CASENOTE_LENGTH_BYTES) {
      this
    } else {
      substring(0, MAX_CASENOTE_LENGTH_BYTES - (Utf8.encodedLength(this) - length) - seeDpsReplacement.length) + seeDpsReplacement
    }
}

data class MismatchCaseNote(
  val offenderNo: String,
  val diffsForDps: Set<String> = emptySet(),
  val diffsForNomis: Set<Long> = emptySet(),
  var notes: List<String> = emptyList(),
)

data class CommonCaseNoteFields(
  val text: String,
  val type: String,
  val subType: String,
  val occurrenceDateTime: String?,
  val creationDateTime: String?,
  val dpsUsername: String?,
  val nomisUsernames: List<String>?,
  val amendments: Set<CommonAmendmentFields>,
  val legacyId: Long,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CommonCaseNoteFields) return false
    return text == other.text &&
      equalTypes(other) &&
      subType == other.subType &&
      occurrenceDateTime == other.occurrenceDateTime &&
      creationDateTime == other.creationDateTime &&
      equalUsers(other) &&
      // OMS_OWNER vs XTAG
      equalAmendments(this, other)
  }

  private fun equalTypes(nomis: CommonCaseNoteFields): Boolean = (type == nomis.type || (type == "APP" && nomis.type == "CNOTE" && subType == "OUTCOME"))

  private fun equalUsers(other: CommonCaseNoteFields): Boolean {
    val equal = other.nomisUsernames?.contains(dpsUsername) == true || dpsUsername == "OMS_OWNER"
    if (!equal) {
      CaseNotesReconciliationService.log.info("authorUsername not equal: $dpsUsername !in ${other.nomisUsernames}")
    }
    return equal
  }

  override fun hashCode(): Int = javaClass.hashCode()

  override fun toString(): String = "{id=$legacyId, text-hash=${Objects.hashCode(text)}, type=$type, subType=$subType, occurrenceDateTime=$occurrenceDateTime, creationDateTime=$creationDateTime, authorUsername=${dpsUsername ?: nomisUsernames}, amendments=$amendments}"
}

data class CommonAmendmentFields(
  val text: String,
  val occurrenceDateTime: String?,
  val authorUsername: String,
) {
  override fun toString(): String = "{text-hash=${Objects.hashCode(text)}, occurrenceDateTime=$occurrenceDateTime, authorUsername=$authorUsername}"
}

private val amendmentComparator = compareBy(
  CommonAmendmentFields::occurrenceDateTime,
  CommonAmendmentFields::text,
  CommonAmendmentFields::authorUsername,
)

private fun equalAmendments(v1: CommonCaseNoteFields, v2: CommonCaseNoteFields): Boolean {
  val a1 = v1.amendments
  val a2 = v2.amendments
  if (a1.size != a2.size) {
    return false
  }
  if ((a1 zip a2).all { (a, b) -> a == b }) {
    return true
  }
  return false
}

// calculate the exclusive or (aka symmetric difference or disjunctive union) of two sets
infix fun <T> Set<T>.xor(that: Set<T>): Set<T> = (this - that) + (that - this)
