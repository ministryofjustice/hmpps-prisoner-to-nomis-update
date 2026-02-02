package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsReconciliationService.Companion.dpsOpenValues
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails.Status
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.StaffInvolvement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentAgencyId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.String
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsDpsApiService.Companion.openStatusValues as dpsOpenStatusValues

@Service
class IncidentsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsIncidentsApiService: IncidentsDpsApiService,
  private val nomisIncidentsApiService: IncidentsNomisApiService,
  @Value($$"${reports.incidents.reconciliation.page-size:20}")
  private val pageSize: Long = 20,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val dpsOpenValues = dpsOpenStatusValues.map { it.value }
  }

  suspend fun incidentsReconciliation() {
    nomisIncidentsApiService.getAgenciesWithIncidents()
      .also { agencyIds ->
        telemetryClient.trackEvent(
          "incidents-reports-reconciliation-requested",
          mapOf("prisonCount" to agencyIds.size),
        )

        runCatching { generateReconciliationReport(agencyIds) }
          .onSuccess {
            log.info("Incidents reconciliation report completed with ${it.size} mismatches")
            telemetryClient.trackEvent(
              "incidents-reports-reconciliation-report",
              mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asMap(),
            )
          }
          .onFailure {
            telemetryClient.trackEvent("incidents-reports-reconciliation-report", mapOf("success" to "false"))
            log.error("Incidents reconciliation report failed", it)
          }
      }
  }

  private fun List<MismatchIncidents>.asMap(): Map<String, String> = this.associate {
    it.agencyId to
      ("open-dps=${it.dpsOpenIncidents}:open-nomis=${it.nomisOpenIncidents}; closed-dps=${it.dpsClosedIncidents}:closed-nomis=${it.nomisClosedIncidents}")
  }

  suspend fun generateReconciliationReport(agencies: List<IncidentAgencyId>): List<MismatchIncidents> = agencies.chunked(pageSize.toInt()).flatMap { pagedAgencies ->
    withContext(Dispatchers.Unconfined) {
      pagedAgencies.map { async { checkIncidentsMatch(it.agencyId) } }
    }.awaitAll().filterNotNull()
  }

  suspend fun checkIncidentsMatch(agencyId: String): MismatchIncidents? = runCatching {
    val nomisIncidents = doApiCallWithRetries { nomisIncidentsApiService.getAgencyIncidentCounts(agencyId) }
    val dpsOpenIncidentsCount = doApiCallWithRetries { dpsIncidentsApiService.getOpenIncidentsCount(agencyId) }
    val dpsClosedIncidentsCount = doApiCallWithRetries { dpsIncidentsApiService.getClosedIncidentsCount(agencyId) }
    val nomisOpenIncidentsCount = nomisIncidents.incidentCount.openIncidents

    val nomisClosedIncidentsCount = nomisIncidents.incidentCount.closedIncidents
    val openIncidentsMisMatch = checkOpenIncidentsMatch(agencyId, nomisOpenIncidentsCount)

    log.debug(
      "Incidents: Checking for $agencyId; Nomis->open:$nomisOpenIncidentsCount;closed:$nomisClosedIncidentsCount; " +
        "DPS->open:$dpsOpenIncidentsCount;closed:$dpsClosedIncidentsCount",
    )

    // Only check for open mismatch count when dpsCount is greater than NomisCount to allow comparison of Nomis OPEN incidents against Dps DRAFT incidents
    val result = if (nomisOpenIncidentsCount < dpsOpenIncidentsCount || nomisClosedIncidentsCount != dpsClosedIncidentsCount) {
      MismatchIncidents(
        agencyId = agencyId,
        dpsOpenIncidents = dpsOpenIncidentsCount,
        nomisOpenIncidents = nomisOpenIncidentsCount,
        dpsClosedIncidents = dpsClosedIncidentsCount,
        nomisClosedIncidents = nomisClosedIncidentsCount,
        mismatchOpenIncidents = openIncidentsMisMatch,
      )
        .also { mismatch ->
          log.info("Incidents Mismatch found  $mismatch")
          telemetryClient.trackEvent(
            "incidents-reports-reconciliation-mismatch",
            mapOf(
              "agencyId" to mismatch.agencyId,
              "dpsOpenIncidents" to mismatch.dpsOpenIncidents,
              "nomisOpenIncidents" to mismatch.nomisOpenIncidents,
              "dpsClosedIncidents" to mismatch.dpsClosedIncidents,
              "nomisClosedIncidents" to mismatch.nomisClosedIncidents,
            ),
          )
        }
    } else {
      if (openIncidentsMisMatch.isNotEmpty()) {
        MismatchIncidents(
          agencyId = agencyId,
          dpsOpenIncidents = dpsOpenIncidentsCount,
          nomisOpenIncidents = nomisOpenIncidentsCount,
          dpsClosedIncidents = dpsClosedIncidentsCount,
          nomisClosedIncidents = nomisClosedIncidentsCount,
          mismatchOpenIncidents = openIncidentsMisMatch,
        )
      } else {
        null
      }
    }
    return result
  }.onFailure {
    log.error("Unable to match incidents for agency with $agencyId ", it)
    telemetryClient.trackEvent(
      "incidents-reports-reconciliation-mismatch-error",
      mapOf(
        "agencyId" to agencyId,
      ),
    )
  }.getOrNull()

  suspend fun checkOpenIncidentsMatch(agencyId: String, openIncidentCount: Long): List<MismatchIncident> = openIncidentCount.asPages(pageSize).flatMap { page ->
    val openIncidentIds = getOpenIncidentsForPage(agencyId, page)
    openIncidentIds.mapNotNull {
      val nomisOpenIncidentId = it.incidentId
      runCatching {
        checkIncidentMatch(nomisOpenIncidentId)
      }
        .onSuccess {
          log.debug("Checking Incident (onSuccess: $nomisOpenIncidentId)")
        }.onFailure {
          log.error("Unable to match agency for incident: $nomisOpenIncidentId", it)
          telemetryClient.trackEvent(
            "incidents-reports-reconciliation-detail-mismatch-error",
            mapOf("nomisId" to nomisOpenIncidentId),
          )
        }.getOrNull()
    }
  }

  private suspend fun getOpenIncidentsForPage(agencyId: String, page: Pair<Long, Long>) = runCatching { nomisIncidentsApiService.getOpenIncidentIds(agencyId, page.first, page.second) }
    .onFailure {
      telemetryClient.trackEvent(
        "incidents-reports-reconciliation-mismatch-page-error",
        mapOf(
          "page" to page.first.toString(),
        ),
      )
      log.error("Unable to match entire page of incidents: $page", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("Page requested: $page, with ${it.size} open incidents") }

  suspend fun checkIncidentMatch(nomisOpenIncidentId: Long): MismatchIncident? {
    val (nomisOpenIncident, dpsOpenIncident) =
      withContext(Dispatchers.Unconfined) {
        async { doApiCallWithRetries { nomisIncidentsApiService.getIncident(nomisOpenIncidentId) } } to
          async { doApiCallWithRetries { dpsIncidentsApiService.getIncidentDetailsByNomisId(nomisOpenIncidentId) } }
      }.awaitBoth()

    val verdict = doesNotMatch(nomisOpenIncident, dpsOpenIncident)
    log.debug(
      "Incidents-NomisIncidentId:{}; DPSIncidentId:{} matchVerdict: {}",
      nomisOpenIncidentId,
      dpsOpenIncident.id,
      verdict,
    )

    return if (verdict != null) {
      MismatchIncident(
        nomisId = nomisOpenIncident.incidentId,
        dpsId = dpsOpenIncident.id,
        nomisIncident = nomisOpenIncident.toReportDetail(),
        dpsIncident = dpsOpenIncident.toReportDetail(),
        verdict = verdict,
      )
        .also { mismatch ->
          log.info("Incident mismatch found $mismatch")
          telemetryClient.trackEvent(
            "incidents-reports-reconciliation-detail-mismatch",
            mapOf(
              "nomisId" to mismatch.nomisId,
              "dpsId" to mismatch.dpsId,
              "verdict" to verdict,
              "nomis" to (mismatch.nomisIncident?.toString() ?: "null"),
              "dps" to (mismatch.dpsIncident?.toString() ?: "null"),
            ),
          )
        }
    } else {
      null
    }
  }

  internal fun doesNotMatch(
    nomis: IncidentResponse,
    dps: ReportWithDetails,
  ): String? {
    // Check and ignore any draft incidents that have previously been open.  Any changes when in draft are not relayed back to Nomis.
    if (dps.isInDraftButPreviouslyOpen()) {
      return null
    }

    // Note. lastModifiedDateTime should not be compared here as merge updates are not passed through to DPS,
    // and therefore the values will be out of sync
    if (nomis.reportingStaff.username != dps.reportedBy) return "Reporting Staff mismatch"
    if (nomis.offenderParties.size != dps.prisonersInvolved.size) return "Offender parties mismatch"
    if (nomis.staffParties.size != dps.nomisOnlyStaff().size) return "Staff parties mismatch"
    if (nomis.type != dps.type.mapDps()) return "type mismatch"
    if (!dps.isValidStatus(nomis)) return "status mismatch"
    if (nomis.reportedDateTime != dps.reportedAt.truncatedTo(ChronoUnit.SECONDS)) {
      return "reported date mismatch"
    }
    val offendersDifference =
      nomis.offenderParties.map { it.offender.offenderNo }.compare(dps.prisonersInvolved.map { it.prisonerNumber })
    if (offendersDifference.isNotEmpty()) return "Offender parties mismatch $offendersDifference"

    if (nomis.requirements.size != dps.correctionRequests.size) return "requirements mismatch"
    if (nomis.questions.size != dps.questions.size) {
      return "questions mismatch"
    }
    nomis.questions.forEachIndexed { i, nomisQuestion ->
      val dpsQuestion = dps.questions[i]
      if (dpsQuestion.code != nomisQuestion.questionId.toString()) {
        return "Code mismatch for question: ${nomisQuestion.questionId}"
      }
      if (nomisQuestion.answers.size != dpsQuestion.responses.size) {
        // Ignore and log any dodgy Nomis data
        if (!nomisQuestion.hasMultipleAnswers && nomisQuestion.answers.size > 1) {
          telemetryClient.trackEvent(
            "incidents-reports-reconciliation-mismatch-multiple-answers-ignored",
            mapOf(
              "nomisIncidentId" to nomis.incidentId,
              "dpsIncidentId" to dps.id,
              "questionId" to nomisQuestion.questionId,
              "hasMultipleAnswers" to false,
              "totalResponses" to nomisQuestion.answers.size,
            ),
          )
        } else {
          return "responses mismatch for question: ${nomisQuestion.questionId}"
        }
      }
    }
    return null
  }

  fun List<String>.compare(otherList: List<String>): List<String> {
    val both = (this + otherList).toSet()
    return both.filterNot { this.contains(it) && otherList.contains(it) }
  }
}

fun ReportWithDetails.isValidStatus(nomis: IncidentResponse): Boolean = if (status == Status.DRAFT) {
  historyOfStatuses.any { it.status.value in dpsOpenValues }
} else {
  nomis.status.code == status.mapDps()
}

fun ReportWithDetails.isInDraftButPreviouslyOpen(): Boolean = status == Status.DRAFT && historyOfStatuses.any { it.status.value in dpsOpenValues }
fun ReportWithDetails.nomisOnlyStaff(): List<StaffInvolvement> = staffInvolved.filter { it.staffUsername != null }

data class MismatchIncidents(
  val agencyId: String,
  val dpsOpenIncidents: Long,
  val nomisOpenIncidents: Long,
  val dpsClosedIncidents: Long,
  val nomisClosedIncidents: Long,
  val mismatchOpenIncidents: List<MismatchIncident> = listOf(),
)

data class MismatchIncident(
  val nomisId: Long,
  val dpsId: UUID,
  val nomisIncident: IncidentReportDetail? = null,
  val dpsIncident: IncidentReportDetail? = null,
  val verdict: String,
)

data class IncidentReportDetail(
  val type: String? = null,
  val status: String? = null,
  val reportedBy: String,
  val reportedDateTime: LocalDateTime,
  val offenderParties: List<String>? = null,
  val totalStaffParties: Int? = null,
  val totalQuestions: Int? = null,
  val totalRequirements: Int? = null,
  val totalResponses: Int? = null,
)

fun IncidentResponse.toReportDetail() = IncidentReportDetail(
  type,
  status.code,
  reportingStaff.username,
  reportedDateTime,
  offenderParties.map { it.offender.offenderNo },
  staffParties.size,
  questions.size,
  requirements.size,
  questions.flatMap { it.answers }.size,
)

fun ReportWithDetails.toReportDetail() = IncidentReportDetail(
  type.mapDps(),
  if (status == Status.DRAFT) "DRAFT" else status.mapDps(),
  reportedBy,
  reportedAt.truncatedTo(ChronoUnit.SECONDS),
  prisonersInvolved.map { it.prisonerNumber },
  nomisOnlyStaff().size,
  questions.size,
  correctionRequests.size,
  questions.flatMap { it.responses }.size,
)
