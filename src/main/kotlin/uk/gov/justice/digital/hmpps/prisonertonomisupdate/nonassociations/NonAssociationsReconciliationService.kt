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
  private val pageSize: Long = 20,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(nonAssociationsCount: Long): List<List<MismatchNonAssociation>> {
    val allNomisIds = mutableSetOf<NonAssociationIdResponse>()
    val results = nonAssociationsCount.asPages(pageSize).flatMap { page ->
      val nonAssociations = getNonAssociationsForPage(page)

      allNomisIds.addAll(nonAssociations)

      withContext(Dispatchers.Unconfined) {
        nonAssociations.map { async { checkMatch(it) } }
      }.awaitAll().filterNot { it.isEmpty() }
    }
    return results + listOf(checkForMissingDpsRecords(allNomisIds))
  }

  internal suspend fun checkForMissingDpsRecords(allNomisIds: Set<NonAssociationIdResponse>): List<MismatchNonAssociation> {
    val allDpsIds = nonAssociationsApiService.getAllNonAssociations(0, 1).totalElements
    if (allDpsIds.toInt() != allNomisIds.size) {
      log.info("Total no of NAs does not match: DPS=$allDpsIds, Nomis=${allNomisIds.size}")
      telemetryClient.trackEvent(
        "non-associations-reports-reconciliation-mismatch",
        mapOf("missing-dps-records" to "true", "dps-total" to allDpsIds.toString(), "nomis-total" to allNomisIds.size.toString()),
      )
      return allDpsIds.asPages(100).flatMap { page ->
        val dpsPage = nonAssociationsApiService.getAllNonAssociations(page.first, page.second).content
        dpsPage
          .filterNot {
            val dpsId = if (it.firstPrisonerNumber < it.secondPrisonerNumber) {
              NonAssociationIdResponse(it.firstPrisonerNumber, it.secondPrisonerNumber)
            } else {
              NonAssociationIdResponse(it.secondPrisonerNumber, it.firstPrisonerNumber)
            }
            allNomisIds.contains(dpsId)
          }
          .map {
            log.info("NonAssociation Mismatch found $it")
            telemetryClient.trackEvent(
              "non-associations-reports-reconciliation-mismatch",
              mapOf(
                "offenderNo1" to it.firstPrisonerNumber,
                "offenderNo2" to it.secondPrisonerNumber,
                "dps" to it.toString(),
              ),
            )

            MismatchNonAssociation(
              NonAssociationIdResponse(it.firstPrisonerNumber, it.secondPrisonerNumber),
              null,
              NonAssociationReportDetail(
                it.restrictionType.name,
                it.whenCreated,
                "",
                it.isClosed,
                it.firstPrisonerRole.name,
                it.secondPrisonerRole.name,
                it.reason.name,
                it.comment,
              ),
            )
          }
      }
    }
    return emptyList()
  }

  internal suspend fun getNonAssociationsForPage(page: Pair<Long, Long>) =
    runCatching { nomisApiService.getNonAssociations(page.first, page.second).content }
      .onFailure {
        telemetryClient.trackEvent(
          "non-associations-reports-reconciliation-mismatch-page-error",
          mapOf("page" to page.first.toString()),
        )
        log.error("Unable to match entire page of prisoners: $page", it)
      }
      .getOrElse { emptyList() }
      .also { log.info("Page requested: $page, with ${it.size} non-associations") }

  internal suspend fun checkMatch(id: NonAssociationIdResponse): List<MismatchNonAssociation> = runCatching {
    // log.debug("Checking NA: ${id.offenderNo1}, ${id.offenderNo2}")
    val (nomisListUnsorted, dpsListUnsorted) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getNonAssociationDetails(id.offenderNo1, id.offenderNo2) } to
        async { nonAssociationsApiService.getNonAssociationsBetween(id.offenderNo1, id.offenderNo2) }
    }.awaitBoth()

    val nomisListSorted = nomisListUnsorted.sortedBy { it.effectiveDate }
    // Ignore old open records
    val closedPlusOpenLists = nomisListSorted.partition { it.expiryDate != null }
    val nomisList = closedPlusOpenLists.first + closedPlusOpenLists.second.takeLast(1)

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
        .filter { doesNotMatch(nomisList[it], dpsList[it]) }
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
                "",
                nomisList[index].comment,
              ),
              NonAssociationReportDetail(
                dpsList[index].restrictionType.name,
                dpsList[index].whenCreated,
                "",
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
      ((nomis.expiryDate != null && nomis.expiryDate.isBefore(today)) xor dps.isClosed) ||
      reasonDoesNotMatch(nomis.reason, nomis.recipReason, dps.firstPrisonerRole, dps.secondPrisonerRole, dps.reason) ||
      (nomis.comment == null && dps.comment != NO_COMMENT_PROVIDED) || (nomis.comment != null && nomis.comment != dps.comment)
  }

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
    return t.first != role1 || t.second != role2 || ((reason == "BUL" || reason == "RIV") && t.third != dpsReasonEnum)
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
