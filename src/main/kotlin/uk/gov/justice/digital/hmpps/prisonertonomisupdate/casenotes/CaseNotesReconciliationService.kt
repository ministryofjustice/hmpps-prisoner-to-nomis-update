package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

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
import java.time.format.DateTimeFormatter
import java.util.Objects
import kotlin.String

@Service
class CaseNotesReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val caseNotesDpsApiService: CaseNotesDpsApiService,
  private val caseNotesNomisApiService: CaseNotesNomisApiService,
  private val nomisApiService: NomisApiService,
  @Value("\${reports.casenotes.reconciliation.page-size}")
  private val pageSize: Long = 20,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(): List<MismatchCaseNote> {
    log.info("started service generateReconciliationReport()")
    var last: Long = 0
    var size: Int = 0
    var pageNumber = 0
    var pageErrors = 0
    val results = mutableListOf<MismatchCaseNote>()
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
    return results
  }

  suspend fun checkMatch(prisonerId: PrisonerId): MismatchCaseNote? {
    val offenderNo = prisonerId.offenderNo

    return runCatching {
      val nomisCaseNotes = caseNotesNomisApiService.getCaseNotesForPrisoner(offenderNo)
        .caseNotes
        .map(::transformFromNomis)
        .toSortedSet(fieldsComparator)

      val dpsCaseNotes = caseNotesDpsApiService.getCaseNotesForPrisoner(offenderNo)
        .map(::transformFromDps)
        .toSortedSet(fieldsComparator)

      val pairedCaseNotes = dpsCaseNotes zip nomisCaseNotes
      return if (dpsCaseNotes.size != nomisCaseNotes.size || pairedCaseNotes.any { (d, n) -> d != n }) {
        val missingFromNomis = dpsCaseNotes - nomisCaseNotes
        val missingFromDps = nomisCaseNotes - dpsCaseNotes
        if (!missingFromDps.isEmpty() || !missingFromNomis.isEmpty()) {
          log.info("missingFromNomis = $missingFromNomis")
          log.info("missingFromDps   = $missingFromDps")
          telemetryClient.trackEvent(
            "casenotes-reports-reconciliation-mismatch",
            mapOf(
              "offenderNo" to offenderNo,
              "missingFromDps" to (missingFromDps.joinToString()),
              "missingFromNomis" to (missingFromNomis.joinToString()),
            ),
          )
          MismatchCaseNote(offenderNo, missingFromDps, missingFromNomis)
        } else {
          val diffs = pairedCaseNotes.filter { (a, b) -> a != b }
          val dpsDiffs = diffs.map { it.first }
          val diffsNomis = diffs.map { it.second }
          log.info("diffs are dps = $dpsDiffs")
          log.info("        nomis = $diffsNomis")
          // log.info("CaseNotes Mismatch found: prisoner $offenderNo, dps total = ${dpsCaseNotes.size}, nomis total = ${nomisCaseNotes.size}, missingFromNomis = ${missingFromNomis.size}, missingFromDps = ${missingFromDps.size}")
          telemetryClient.trackEvent(
            "casenotes-reports-reconciliation-mismatch",
            mapOf(
              "offenderNo" to offenderNo,
              "diffs-dps" to (dpsDiffs.joinToString()),
              "diffs-nomis" to (diffsNomis.joinToString()),
            ),
          )
          MismatchCaseNote(offenderNo = offenderNo, missingFromDps = missingFromDps, missingFromNomis = missingFromNomis)
        }
      } else {
        null
      }
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

  private fun transformFromDps(c: CaseNote) = CommonCaseNoteFields(
    c.text,
    c.type,
    c.subType,
    c.occurrenceDateTime.format(DateTimeFormatter.ISO_DATE_TIME),
    c.authorUsername,
    c.amendments.map { a ->
      CommonAmendmentFields(
        text = a.additionalNoteText,
        occurrenceDateTime = a.creationDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
        authorUsername = a.authorUserName,
      )
    }.toSortedSet(amendmentComparator),
    c.legacyId,
  )

  private fun transformFromNomis(c: CaseNoteResponse) = CommonCaseNoteFields(
    c.caseNoteText,
    c.caseNoteType.code,
    c.caseNoteSubType.code,
    c.occurrenceDateTime,
    c.authorUsername,
    c.amendments.map { a ->
      CommonAmendmentFields(
        text = a.text,
        occurrenceDateTime = a.createdDateTime,
        authorUsername = a.authorUsername,
      )
    }.toSortedSet(amendmentComparator),
    c.caseNoteId,
  )
}

data class MismatchCaseNote(
  val offenderNo: String,
  val missingFromDps: Set<CommonCaseNoteFields>,
  val missingFromNomis: Set<CommonCaseNoteFields>,
)

data class CommonCaseNoteFields(
  val text: String,
  val type: String,
  val subType: String,
  val occurrenceDateTime: String?,
  val authorUsername: String,
  val amendments: Set<CommonAmendmentFields>,
  val legacyId: Long,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    other as CommonCaseNoteFields
    return text == other.text &&
      type == other.type &&
      subType == other.subType &&
      occurrenceDateTime == other.occurrenceDateTime &&
      equalUsers(other) && // OMS_OWNER vs XTAG
      equalAmendments(this, other)
  }

  private fun equalUsers(other: CommonCaseNoteFields): Boolean {
    val equal = authorUsername == other.authorUsername || authorUsername == "OMS_OWNER"
    if (!equal) {
      CaseNotesReconciliationService.log.info("authorUsername not equal: $authorUsername != $(other.authorUsername}")
    }
    return equal
  }

  override fun hashCode(): Int = javaClass.hashCode()

  override fun toString(): String {
    return "{id=$legacyId text-hash=${Objects.hashCode(text)}, type=$type, subType=$subType, occurrenceDateTime=$occurrenceDateTime, authorUsername=$authorUsername, amendments=$amendments}"
  }
}

data class CommonAmendmentFields(
  val text: String,
  val occurrenceDateTime: String?,
  val authorUsername: String,
) {
  override fun toString(): String {
    return "{text-hash=${Objects.hashCode(text)}, occurrenceDateTime=$occurrenceDateTime, authorUsername=$authorUsername}"
  }
}

private val fieldsComparator = compareBy(
  CommonCaseNoteFields::legacyId,
)

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
