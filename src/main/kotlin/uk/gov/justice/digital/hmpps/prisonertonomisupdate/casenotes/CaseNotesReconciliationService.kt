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
      var size: Int = 0
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
      return results
    }
  }

  internal suspend fun getAllPrisonersForPage(page: Pair<Long, Long>) =
    runCatching {
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
      val mappings = caseNotesMappingApiService.getByPrisoner(offenderNo).mappings

      val dpsDistinctIds = mappings.map { it.dpsCaseNoteId }.distinct()

      val dpsCaseNotes = caseNotesDpsApiService.getCaseNotesForPrisoner(offenderNo)
        .associate { it.caseNoteId to it.transformFromDps() }

      val originals = caseNotesNomisApiService.getCaseNotesForPrisoner(offenderNo).caseNotes

      val nomisCaseNotes = originals
        .associate { it.caseNoteId to it.transformFromNomis() }

      val message = "mappings.size = ${mappings.size}, dpsDistinctIds.size = ${dpsDistinctIds.size}, nomisCaseNotes.size = ${nomisCaseNotes.size}, dpsCaseNotes.size = ${dpsCaseNotes.size}"
      val mismatchCaseNote = MismatchCaseNote(offenderNo, emptySet(), emptySet())

      if (mappings.size != nomisCaseNotes.size) {
        log.info("prisoner $offenderNo : $message")
        telemetryClient.trackEvent(
          "casenotes-reports-reconciliation-mismatch-size",
          mapOf("offenderNo" to offenderNo, "message" to message),
        )
        mismatchCaseNote.notes += message
        return mismatchCaseNote
      }

      if (dpsDistinctIds.size != dpsCaseNotes.size) {
        log.info("prisoner $offenderNo : $message")
        telemetryClient.trackEvent(
          "casenotes-reports-reconciliation-mismatch-size",
          mapOf("offenderNo" to offenderNo, "message" to message),
        )
        mismatchCaseNote.notes += message
        return mismatchCaseNote
      }

      mappings.forEach {
        val dpsCaseNote = dpsCaseNotes[it.dpsCaseNoteId]
        val nomisCaseNote = nomisCaseNotes[it.nomisCaseNoteId]
        if (dpsCaseNote != nomisCaseNote) {
          "dpsCaseNote = $dpsCaseNote, nomisCaseNote = $nomisCaseNote"
            .also {
              log.info("prisoner $offenderNo : $it")
              mismatchCaseNote.notes += it
            }
        }
      }
      return if (mismatchCaseNote.notes.isEmpty()) {
        null
      } else {
        var i = 0
        telemetryClient.trackEvent(
          "casenotes-reports-reconciliation-mismatch",
          mapOf(
            "offenderNo" to offenderNo,
          ) + mismatchCaseNote.notes.associate { (++i).toString() to it },
        )
        mismatchCaseNote
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

  private fun CaseNote.transformFromDps() = CommonCaseNoteFields(
    text,
    type,
    subType,
    occurrenceDateTime.format(DateTimeFormatter.ISO_DATE_TIME),
    authorUsername,
    amendments.map { a ->
      CommonAmendmentFields(
        text = a.additionalNoteText,
        occurrenceDateTime = a.creationDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
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
    authorUsername,
    amendments.map { a ->
      CommonAmendmentFields(
        text = a.text,
        occurrenceDateTime = a.createdDateTime,
        authorUsername = a.authorUsername,
      )
    }.toSortedSet(amendmentComparator),
    caseNoteId,
  )
}

data class MismatchCaseNote(
  val offenderNo: String,
  val missingFromDps: Set<CommonCaseNoteFields>,
  val missingFromNomis: Set<CommonCaseNoteFields>,
  var notes: List<String> = mutableListOf(),
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
      equalTypes(other) &&
      subType == other.subType &&
      occurrenceDateTime == other.occurrenceDateTime &&
      equalUsers(other) && // OMS_OWNER vs XTAG
      equalAmendments(this, other)
  }

  private fun equalTypes(nomis: CommonCaseNoteFields): Boolean = (type == nomis.type || (type == "APP" && nomis.type == "CNOTE" && subType == "OUTCOME"))

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
