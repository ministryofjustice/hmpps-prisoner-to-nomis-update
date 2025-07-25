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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NonAssociationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model.NonAssociation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.time.LocalDate

@Service
class NonAssociationsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val nonAssociationsApiService: NonAssociationsApiService,
  @Value("\${reports.non-associations.reconciliation.page-size}")
  private val pageSize: Long,
) {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(nonAssociationsCount: Long): List<List<MismatchNonAssociation>> {
    val allNomisIds = mutableSetOf<NonAssociationIdResponse>()
    val results = nonAssociationsCount.asPages(pageSize).flatMap { page ->
      val nonAssociations = getNomisNonAssociationsForPage(page)

      allNomisIds.addAll(nonAssociations)

      withContext(Dispatchers.Unconfined) {
        nonAssociations.map { async { checkMatch(correctNomsIdOrder(it)) } }
      }.awaitAll()
    }
    val mismatches = results.map { it.first }
    val counts = results.sumOf { it.second }
    return mismatches + listOf(checkForMissingDpsRecords(allNomisIds, counts))
  }

  private fun correctNomsIdOrder(id: NonAssociationIdResponse): NonAssociationIdResponse = if (id.offenderNo1 < id.offenderNo2) {
    id
  } else {
    NonAssociationIdResponse(id.offenderNo2, id.offenderNo1)
  }

  internal suspend fun checkForMissingDpsRecords(allNomisIds: Set<NonAssociationIdResponse>, nomisTotalDetails: Int): List<MismatchNonAssociation> {
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
              it.whenCreated.toLocalDate(),
              null,
              it.isClosed,
              it.firstPrisonerRole.name,
              it.secondPrisonerRole.name,
              it.reason.name,
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

  internal suspend fun getNomisNonAssociationsForPage(page: Pair<Long, Long>) = runCatching { nomisApiService.getNonAssociations(page.first, page.second).content }
    .onFailure {
      telemetryClient.trackEvent(
        "non-associations-reports-reconciliation-mismatch-page-error",
        mapOf("page" to page.first.toString()),
      )
      log.error("Unable to match entire Nomis page of prisoners: $page", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("Nomis Page requested: $page, with ${it.size} non-associations") }

  internal suspend fun getDpsNonAssociationsForPage(page: Pair<Long, Long>): List<NonAssociation> = runCatching { nonAssociationsApiService.getAllNonAssociations(page.first, page.second).content }
    .onFailure {
      telemetryClient.trackEvent(
        "non-associations-reports-reconciliation-mismatch-page-error",
        mapOf("page" to page.first.toString()),
      )
      log.error("Unable to match entire DPS page of prisoners: $page", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("DPS Page requested: $page, with ${it.size} non-associations") }

  internal suspend fun checkMatch(id: NonAssociationIdResponse): Pair<List<MismatchNonAssociation>, Int> = runCatching {
    return checkMatchOrThrowException(id)
  }.onFailure {
    log.error("Unable to match non-associations for id: ${id.offenderNo1}, ${id.offenderNo2}", it)
    telemetryClient.trackEvent(
      "non-associations-reports-reconciliation-mismatch-error",
      mapOf(
        "offenderNo1" to id.offenderNo1,
        "offenderNo2" to id.offenderNo2,
      ),
    )
  }.getOrDefault(emptyList<MismatchNonAssociation>() to 0)

  internal suspend fun checkMatchOrThrowException(id: NonAssociationIdResponse): Pair<List<MismatchNonAssociation>, Int> {
    val today = LocalDate.now()

    val (nomisListUnsorted, dpsListUnsorted) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getNonAssociationDetails(id.offenderNo1, id.offenderNo2) } to
        async { nonAssociationsApiService.getNonAssociationsBetween(id.offenderNo1, id.offenderNo2) }
    }.awaitBoth()

    val nomisListSortedBySequence = nomisListUnsorted.sortedBy { it.typeSequence }
    // Ignore old open records
    val closedPlusOpenLists = nomisListSortedBySequence.partition { closedInNomis(it, today) }
    val nomisList = (closedPlusOpenLists.first + closedPlusOpenLists.second.takeLast(1))
      // needed to change sort order to date to compare against matching DPS records
      // when dates are the same then sort by typeSequence or id which should reflect the order they were created
      .sortedWith(compareBy(NonAssociationResponse::effectiveDate, NonAssociationResponse::typeSequence))

    val dpsList = dpsListUnsorted.sortedWith(compareBy(NonAssociation::whenCreated, NonAssociation::id))

    val mismatches = if (nomisList.size > dpsList.size) {
      (dpsList.size..<nomisList.size)
        .map { index ->
          MismatchNonAssociation(
            id,
            NonAssociationReportDetail(
              nomisList[index].type,
              nomisList[index].effectiveDate,
              nomisList[index].expiryDate,
              null,
              nomisList[index].reason,
              nomisList[index].recipReason,
              null,
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
      (nomisList.size..<dpsList.size)
        .map { index ->
          MismatchNonAssociation(
            id,
            null,
            NonAssociationReportDetail(
              dpsList[index].restrictionType.name,
              dpsList[index].whenCreated.toLocalDate(),
              null,
              dpsList[index].isClosed,
              dpsList[index].firstPrisonerRole.name,
              dpsList[index].secondPrisonerRole.name,
              dpsList[index].reason.name,
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
        .filter {
          doesNotMatch(nomisList[it], dpsList[it]) && (dpsList.size != 2 || doesNotMatch(nomisList[0], dpsList[1]))
        }
        .map { index ->
          val mismatch =
            MismatchNonAssociation(
              id,
              NonAssociationReportDetail(
                nomisList[index].type,
                nomisList[index].effectiveDate,
                nomisList[index].expiryDate,
                null,
                nomisList[index].reason,
                nomisList[index].recipReason,
                null,
              ),
              NonAssociationReportDetail(
                dpsList[index].restrictionType.name,
                dpsList[index].whenCreated.toLocalDate(),
                null,
                dpsList[index].isClosed,
                dpsList[index].firstPrisonerRole.name,
                dpsList[index].secondPrisonerRole.name,
                dpsList[index].reason.name,
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
    return mismatches to nomisList.size
  }

  internal fun doesNotMatch(
    nomis: NonAssociationResponse,
    dps: NonAssociation,
  ): Boolean {
    val today = LocalDate.now()
    return typeDoesNotMatch(nomis.type, dps.restrictionType) ||
      (!nomis.effectiveDate.isAfter(today) && nomis.effectiveDate != dps.whenCreated.toLocalDate()) ||
      (closedInNomis(nomis, today) xor dps.isClosed)
  }

  internal fun closedInNomis(nomis: NonAssociationResponse, today: LocalDate?): Boolean {
    val expiryDate = nomis.expiryDate
    return (expiryDate != null && !expiryDate.isAfter(today)) || nomis.effectiveDate.isAfter(today)
  }

  private fun typeDoesNotMatch(
    nomisType: String,
    dpsType: NonAssociation.RestrictionType,
  ) = when (nomisType) {
    "NONEX", "TNA", "WING" -> dpsType != NonAssociation.RestrictionType.WING
    else -> nomisType != dpsType.name.take(4)
  }
}

data class NonAssociationReportDetail(
  val type: String,
  val createdDate: LocalDate,
  val expiryDate: LocalDate? = null,
  val closed: Boolean? = null,
  val roleReason: String,
  val roleReason2: String,
  val dpsReason: String? = null,
)

data class MismatchNonAssociation(
  val id: NonAssociationIdResponse,
  val nomisNonAssociation: NonAssociationReportDetail?,
  val dpsNonAssociation: NonAssociationReportDetail?,
)
