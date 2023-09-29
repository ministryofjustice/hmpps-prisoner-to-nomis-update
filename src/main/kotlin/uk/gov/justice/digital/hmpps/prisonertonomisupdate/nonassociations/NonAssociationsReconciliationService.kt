package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.NonAssociationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model.NonAssociation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.time.LocalDate

private const val NO_COMMENT_PROVIDED = "No comment provided"

@Service
class NonAssociationsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val nonAssociationsApiService: NonAssociationsApiService,
  @Value("\${reports.non-associations.reconciliation.page-size}")
  private val pageSize: Long,
) {

  var nomisTotalDetails = 0

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(nonAssociationsCount: Long): List<List<MismatchNonAssociation>> {
    val allNomisIds = mutableSetOf<NonAssociationIdResponse>()
    nomisTotalDetails = 0
    val results = nonAssociationsCount.asPages(pageSize).flatMap { page ->
      val nonAssociations = getNomisNonAssociationsForPage(page)

      allNomisIds.addAll(nonAssociations)

      withContext(Dispatchers.Unconfined) {
        nonAssociations.map { async { checkMatch(correctNomsIdOrder(it)) } }
      }.awaitAll().filterNot { it.isEmpty() }
    }
    return results + listOf(checkForMissingDpsRecords(allNomisIds))
  }

  private fun correctNomsIdOrder(id: NonAssociationIdResponse): NonAssociationIdResponse {
    return if (id.offenderNo1 < id.offenderNo2) {
      id
    } else {
      NonAssociationIdResponse(id.offenderNo2, id.offenderNo1)
    }
  }

  internal suspend fun checkForMissingDpsRecords(allNomisIds: Set<NonAssociationIdResponse>): List<MismatchNonAssociation> {
    val allDpsIds = nonAssociationsApiService.getAllNonAssociations(0, 1).totalElements
    if (allDpsIds.toInt() == nomisTotalDetails) {
      log.info("Total no of NAs matches: DPS=$allDpsIds, Nomis=$nomisTotalDetails")
      return emptyList()
    }
    log.info("Total no of NAs does not match: DPS=$allDpsIds, Nomis=$nomisTotalDetails")
    telemetryClient.trackEvent(
      "non-associations-reports-reconciliation-mismatch-missing-dps-records",
      mapOf("dps-total" to allDpsIds.toString(), "nomis-total" to nomisTotalDetails.toString()),
    )
    return allDpsIds.asPages(pageSize).flatMap { page ->
      getDpsNonAssociationsForPage(page)
        .filterNot {
          allNomisIds.contains(NonAssociationIdResponse(it.firstPrisonerNumber, it.secondPrisonerNumber)) ||
            allNomisIds.contains(NonAssociationIdResponse(it.secondPrisonerNumber, it.firstPrisonerNumber))
        }
        .map {
          val mismatch = MismatchNonAssociation(
            NonAssociationIdResponse(it.firstPrisonerNumber, it.secondPrisonerNumber),
            null,
            NonAssociationReportDetail(
              it.restrictionType.name,
              it.whenCreated,
              null,
              it.isClosed,
              it.firstPrisonerRole.name,
              it.secondPrisonerRole.name,
              it.reason.name,
              it.comment,
            ),
          )
          log.info("NonAssociation Mismatch found extra DPS NA $it")
          telemetryClient.trackEvent(
            "non-associations-reports-reconciliation-dps-only",
            mapOf(
              "offenderNo1" to it.firstPrisonerNumber,
              "offenderNo2" to it.secondPrisonerNumber,
              "dps" to mismatch.dpsNonAssociation.toString(),
            ),
          )
          mismatch
        }
    }
  }

  internal suspend fun getNomisNonAssociationsForPage(page: Pair<Long, Long>) =
    runCatching { doApiCallWithRetry { nomisApiService.getNonAssociations(page.first, page.second).content } }
      .onFailure {
        telemetryClient.trackEvent(
          "non-associations-reports-reconciliation-mismatch-page-error",
          mapOf("page" to page.first.toString()),
        )
        log.error("Unable to match entire Nomis page of prisoners: $page", it)
      }
      .getOrElse { emptyList() }
      .also { log.info("Nomis Page requested: $page, with ${it.size} non-associations") }

  internal suspend fun getDpsNonAssociationsForPage(page: Pair<Long, Long>): List<NonAssociation> =
    runCatching { doApiCallWithRetry { nonAssociationsApiService.getAllNonAssociations(page.first, page.second).content } }
      .onFailure {
        telemetryClient.trackEvent(
          "non-associations-reports-reconciliation-mismatch-page-error",
          mapOf("page" to page.first.toString()),
        )
        log.error("Unable to match entire DPS page of prisoners: $page", it)
      }
      .getOrElse { emptyList() }
      .also { log.info("DPS Page requested: $page, with ${it.size} non-associations") }

  internal suspend fun checkMatch(id: NonAssociationIdResponse): List<MismatchNonAssociation> = runCatching {
    // log.debug("Checking NA: ${id.offenderNo1}, ${id.offenderNo2}")

    val today = LocalDate.now()

    val (nomisListUnsorted, dpsListUnsorted) = withContext(Dispatchers.Unconfined) {
      async { doApiCallWithRetry { nomisApiService.getNonAssociationDetails(id.offenderNo1, id.offenderNo2) } } to
        async { doApiCallWithRetry { nonAssociationsApiService.getNonAssociationsBetween(id.offenderNo1, id.offenderNo2) } }
    }.awaitBoth()

    val nomisListSortedBySequence = nomisListUnsorted.sortedBy { it.typeSequence }
    // Ignore old open records
    val closedPlusOpenLists = nomisListSortedBySequence.partition { closedInNomis(it, today) }
    val nomisList = (closedPlusOpenLists.first + closedPlusOpenLists.second.takeLast(1))
      // needed to change sort order to date to compare against matching DPS records
      .sortedBy { it.effectiveDate }

    nomisTotalDetails += nomisList.size

    val dpsList = dpsListUnsorted.sortedBy { it.whenCreated }

    return if (nomisList.size > dpsList.size) {
      // log.info("Extra Nomis details found for ${id.offenderNo1}, ${id.offenderNo2}")
      (dpsList.size..<nomisList.size)
        .map { index ->
          MismatchNonAssociation(
            id,
            NonAssociationReportDetail(
              nomisList[index].type,
              nomisList[index].effectiveDate.toString(),
              nomisList[index].expiryDate?.toString(),
              null,
              nomisList[index].reason,
              nomisList[index].recipReason,
              null,
              nomisList[index].comment,
            ),
            null,
          )
            .also { mismatch ->
              log.info("NonAssociation Mismatch found $mismatch")
              telemetryClient.trackEvent(
                "non-associations-reports-reconciliation-mismatch",
                mapOf(
                  "offenderNo1" to mismatch.id.offenderNo1,
                  "offenderNo2" to mismatch.id.offenderNo2,
                  "typeSequence" to nomisList[index].typeSequence.toString(),
                ),
              )
            }
        }
    } else if (nomisList.size < dpsList.size) {
      // log.info("Extra DPS details found for ${id.offenderNo1}, ${id.offenderNo2}")
      (nomisList.size..<dpsList.size)
        .map { index ->
          MismatchNonAssociation(
            id,
            null,
            NonAssociationReportDetail(
              dpsList[index].restrictionType.name,
              dpsList[index].whenCreated,
              null,
              dpsList[index].isClosed,
              dpsList[index].firstPrisonerRole.name,
              dpsList[index].secondPrisonerRole.name,
              dpsList[index].reason.name,
              dpsList[index].comment,
            ),
          )
            .also { mismatch ->
              log.info("NonAssociation Mismatch found $mismatch")
              telemetryClient.trackEvent(
                "non-associations-reports-reconciliation-mismatch",
                mapOf(
                  "offenderNo1" to mismatch.id.offenderNo1,
                  "offenderNo2" to mismatch.id.offenderNo2,
                ),
              )
            }
        }
    } else {
      dpsList.indices
        .filter { doesNotMatch(nomisList[it], dpsList[it]) && (dpsList.size != 2 || doesNotMatch(nomisList[0], dpsList[1])) }
        .map { index ->
          val mismatch =
            MismatchNonAssociation(
              id,
              NonAssociationReportDetail(
                nomisList[index].type,
                nomisList[index].effectiveDate.toString(),
                nomisList[index].expiryDate?.toString(),
                null,
                nomisList[index].reason,
                nomisList[index].recipReason,
                null,
                nomisList[index].comment,
              ),
              NonAssociationReportDetail(
                dpsList[index].restrictionType.name,
                dpsList[index].whenCreated,
                null,
                dpsList[index].isClosed,
                dpsList[index].firstPrisonerRole.name,
                dpsList[index].secondPrisonerRole.name,
                dpsList[index].reason.name,
                dpsList[index].comment,
              ),
            )
          log.info("NonAssociation Mismatch found $mismatch")
          telemetryClient.trackEvent(
            "non-associations-reports-reconciliation-mismatch",
            mapOf(
              "offenderNo1" to mismatch.id.offenderNo1,
              "offenderNo2" to mismatch.id.offenderNo2,
              "nomis" to (mismatch.nomisNonAssociation?.toString() ?: "null"),
              "dps" to (mismatch.dpsNonAssociation?.toString() ?: "null"),
            ),
          )
          mismatch
        }
    }
  }.onSuccess {
    log.debug("Checking NA (onSuccess: ${id.offenderNo1}, ${id.offenderNo2}")
  }.onFailure {
    log.error("Unable to match non-associations for id: ${id.offenderNo1}, ${id.offenderNo2}", it)
    telemetryClient.trackEvent(
      "non-associations-reports-reconciliation-mismatch-error",
      mapOf(
        "offenderNo1" to id.offenderNo1,
        "offenderNo2" to id.offenderNo2,
      ),
    )
  }.getOrDefault(emptyList())

  private fun doesNotMatch(
    nomis: NonAssociationResponse,
    dps: NonAssociation,
  ): Boolean {
    val today = LocalDate.now()
    return typeDoesNotMatch(nomis.type, dps.restrictionType) ||
      (!nomis.effectiveDate.isAfter(today) && nomis.effectiveDate.toString() != dps.whenCreated.take(10)) ||
      (closedInNomis(nomis, today) xor dps.isClosed) ||
      // reasonDoesNotMatch(nomis.reason, nomis.recipReason, dps.firstPrisonerRole, dps.secondPrisonerRole, dps.reason) ||
      (nomis.comment == null && dps.comment != NO_COMMENT_PROVIDED) || (nomis.comment != null && nomis.comment != dps.comment)
  }

  private fun closedInNomis(nomis: NonAssociationResponse, today: LocalDate?) =
    (nomis.expiryDate != null && nomis.expiryDate.isBefore(today)) || nomis.effectiveDate.isAfter(today)

  private fun typeDoesNotMatch(
    nomisType: String,
    dpsType: NonAssociation.RestrictionType,
  ) = when (nomisType) {
    "NONEX", "TNA", "WING" -> dpsType != NonAssociation.RestrictionType.WING
    else -> nomisType != dpsType.name.take(4)
  }

  private fun reasonDoesNotMatch(
    reason: String,
    recipReason: String,
    role1: NonAssociation.FirstPrisonerRole,
    role2: NonAssociation.SecondPrisonerRole,
    dpsReasonEnum: NonAssociation.Reason,
  ): Boolean {
    val t = translateToRolesAndReason(reason, recipReason)
    return t.first != role1 || t.second != role2 // too strict: || ((reason == "BUL" || reason == "RIV") && t.third != dpsReasonEnum)
  }

  // NOTE this is a copy of the code in SyncAndMigrateService
  fun translateToRolesAndReason(firstPrisonerReason: String, secondPrisonerReason: String): Triple<NonAssociation.FirstPrisonerRole, NonAssociation.SecondPrisonerRole, NonAssociation.Reason> {
    var firstPrisonerRole = NonAssociation.FirstPrisonerRole.UNKNOWN
    var secondPrisonerRole = NonAssociation.SecondPrisonerRole.UNKNOWN
    var reason = NonAssociation.Reason.OTHER

    if (firstPrisonerReason == "BUL") {
      firstPrisonerRole = NonAssociation.FirstPrisonerRole.UNKNOWN
      reason = NonAssociation.Reason.BULLYING
    }
    if (secondPrisonerReason == "BUL") {
      secondPrisonerRole = NonAssociation.SecondPrisonerRole.UNKNOWN
      reason = NonAssociation.Reason.BULLYING
    }

    if (firstPrisonerReason == "RIV") {
      firstPrisonerRole = NonAssociation.FirstPrisonerRole.NOT_RELEVANT
      reason = NonAssociation.Reason.GANG_RELATED
    }
    if (secondPrisonerReason == "RIV") {
      secondPrisonerRole = NonAssociation.SecondPrisonerRole.NOT_RELEVANT
      reason = NonAssociation.Reason.GANG_RELATED
    }

    if (firstPrisonerReason == "VIC") {
      firstPrisonerRole = NonAssociation.FirstPrisonerRole.VICTIM
    }
    if (secondPrisonerReason == "VIC") {
      secondPrisonerRole = NonAssociation.SecondPrisonerRole.VICTIM
    }

    if (firstPrisonerReason == "PER") {
      firstPrisonerRole = NonAssociation.FirstPrisonerRole.PERPETRATOR
    }
    if (secondPrisonerReason == "PER") {
      secondPrisonerRole = NonAssociation.SecondPrisonerRole.PERPETRATOR
    }

    if (firstPrisonerReason == "NOT_REL") {
      firstPrisonerRole = NonAssociation.FirstPrisonerRole.NOT_RELEVANT
    }
    if (secondPrisonerReason == "NOT_REL") {
      secondPrisonerRole = NonAssociation.SecondPrisonerRole.NOT_RELEVANT
    }

    return Triple(firstPrisonerRole, secondPrisonerRole, reason)
  }

  private suspend fun <T> doApiCallWithRetry(apiFun: suspend () -> T) = try {
    apiFun()
  } catch (e: RuntimeException) {
    log.warn("Retrying API call", e)
    apiFun()
  }
}

data class NonAssociationReportDetail(
  val type: String,
  val createdDate: String,
  val expiryDate: String? = null,
  val closed: Boolean? = null,
  val roleReason: String,
  val roleReason2: String,
  val dpsReason: String? = null,
  val comment: String? = null,
)

data class MismatchNonAssociation(
  val id: NonAssociationIdResponse,
  val nomisNonAssociation: NonAssociationReportDetail?,
  val dpsNonAssociation: NonAssociationReportDetail?,
)
