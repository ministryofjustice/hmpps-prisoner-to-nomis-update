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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import java.time.format.DateTimeFormatter
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

  suspend fun generateReconciliationReport(prisonerCount: Long): List<MismatchCaseNote> =
    prisonerCount.asPages(pageSize).flatMap { page ->
      val prisoners = getAllPrisonersForPage(page)

      withContext(Dispatchers.Unconfined) {
        prisoners.map { async { checkMatch(it) } }
      }.awaitAll().filterNotNull()
    }

  internal suspend fun getAllPrisonersForPage(page: Pair<Long, Long>) =
    runCatching { nomisApiService.getAllPrisoners(page.first, page.second).content }
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
      val nomisCaseNotes = caseNotesNomisApiService.getCaseNotesForPrisoner(offenderNo)
        .caseNotes
        .map { c ->
          CommonCaseNoteFields(
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
            },
          )
        }
        .toSortedSet(fieldsComparator)

      val dpsCaseNotes = caseNotesDpsApiService.getCaseNotesForPrisoner(offenderNo)
        .filter { !it.sensitive }
        .map { c ->
          CommonCaseNoteFields(
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
            },
          )
        }
        .toSortedSet(fieldsComparator)

      val pairedCaseNotes = dpsCaseNotes.zip(nomisCaseNotes)
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
          MismatchCaseNote(offenderNo = offenderNo, missingFromDps = missingFromDps, missingFromNomis = missingFromNomis)
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
  val amendments: List<CommonAmendmentFields>,
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
    val equal = authorUsername == "OMS_OWNER" || authorUsername == other.authorUsername
    if (!equal) {
      CaseNotesReconciliationService.log.info("authorUsername not equal: $authorUsername != $(other.authorUsername}")
    }
    return equal
  }

  override fun hashCode(): Int = javaClass.hashCode()
}

data class CommonAmendmentFields(
  val text: String,
  val occurrenceDateTime: String?,
  val authorUsername: String,
)

private val fieldsComparator = compareBy(
  CommonCaseNoteFields::type,
  CommonCaseNoteFields::subType,
  CommonCaseNoteFields::occurrenceDateTime,
  CommonCaseNoteFields::text,
  CommonCaseNoteFields::authorUsername,
)

private fun equalAmendments(v1: CommonCaseNoteFields, v2: CommonCaseNoteFields): Boolean {
  val a1 = v1.amendments
  val a2 = v2.amendments
  if (a1.size != a2.size) {
    return false
  }
  if (a1.zip(a2).all { (a, b) -> a == b }) {
    return true
  }
  return false
}
